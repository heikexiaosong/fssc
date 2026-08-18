public Result generateInteriorDetailCompensate(@RequestBody Map<String, Object> params){
    if ( CollUtil.isEmpty(params) ) {
        return Result.ok();
    }

    String billId = (String)params.get("billId");
    if ( CharSequenceUtil.isBlank(billId) ) {
        return Result.ok();
    }

    Date date = null;
    String dateStr = (String) params.get("date");
    if (StrUtil.isNotBlank(dateStr)) {
        date = DateUtil.parseDate(dateStr).toJdkDate();
    } else {
        date = new Date();
    }

    // 尝试获取锁，如果获取不到说明有其他线程正在执行此操作
    String mutexKey = String.format("%s_%s", GENERATE_INTERIOR_DETAIL_LOCK, billId);
    RLock lock = redis.getDistributeLock(mutexKey);
    try {
        if (!lock.tryLock(3, TimeUnit.SECONDS)) {
            throw new ZteException(MessageUtils.getMessage("inner.bill.interiordetail.refreshing"));
        }
        fmsReceivableBillService.doGenerateInteriorDetail(billId, date);
    } catch (Exception e) {
        throw new ZteException(e.getMessage(), e);
    } finally {
        try {
            if ( lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            logger.error("释放锁失败【{}】",  e.getMessage());
        }
    }
    return Result.ok();
}


public void doGenerateInteriorDetail(String billId, Date date) {
        if ( CharSequenceUtil.isBlank(billId) ) {
            return;
        }

        List<LdgBillAccountDTO> list = coreLdgClient.findListByIds(billId);
        if(CollUtil.isEmpty(list)){
            logger.info("票据id: {}未找到对应的票据信息", ZfsJsonUtil.toJsonStr(billId));
            return;
        }

        // billsToGenerateDetail: 待生成明细的票据列表
        List<LdgBillAccountDTO> billsToGenerateDetail = list.stream()
                .filter(dto -> enableGenerateInteriorDetail(dto))
                .collect(Collectors.toList());
        if ( CollUtil.isEmpty(billsToGenerateDetail) ) {
            return;
        }

        Map<String, List<LdgBillAccountDTO>> groupIdLdgBillAccountsMap = billsToGenerateDetail.stream()
                .filter( dto -> CharSequenceUtil.isNotBlank(dto.getGroupId()))
                .collect(Collectors.groupingBy(LdgBillAccountDTO::getGroupId));

                        List<String> groupIdList = new ArrayList<>(groupIdLdgBillAccountsMap.keySet());
                        Map<String, String> innerDetailLedgerMap = fmsParamsService.getValueByCodeAndGroup(INTERNAL_DETAIL_LEDGER, groupIdList);

                        List<LdgBillAccountDTO> ldgBillAccountList = new ArrayList<>(billsToGenerateDetail.size());
                        Map<String, LdgBillAccountDTO> updateBillMap = new HashMap<>(billsToGenerateDetail.size());
                        List<String> innerIdList = new ArrayList<>();
                        for (Map.Entry<String, List<LdgBillAccountDTO>> entry : groupIdLdgBillAccountsMap.entrySet()) {
                            String groupId = entry.getKey();
                            String shouldGenerateInternalLedger = innerDetailLedgerMap.get(groupId);
                            if (!CharSequenceUtil.equals(Constant.DEFAULT_FLAG_Y, shouldGenerateInternalLedger)) {
                                // INTERNAL_DETAIL_LEDGER: N或者其他代表不生成(Y代表生成)
                                continue;
                            }

                                        List<LdgBillAccountDTO> ldgBillAccountDTOList = entry.getValue();
                                        if ( CollUtil.isEmpty(ldgBillAccountDTOList) ) {
                                            continue;
                                        }

                                        // 查询数据库, 如果已经存在对应的明细账， 则不生成
                                        Set<String> existKey = new HashSet<>();
                                        List<String> billAccountIds = ldgBillAccountDTOList.stream()
                                               .map(LdgBillAccountDTO::getId).collect(Collectors.toList());
                                        List<FmsInteriorDetailDTO> fmsInteriorDetailDTOS = fmsInteriorDetailService.findByBusDetailIdsAndCollectType(billAccountIds
                                                , FmsEnumType.INNER_BUSINESS_TYPE.INNER_BUSINESS_TYPE_062.getCode(), groupId);
                                        if ( CollUtil.isNotEmpty(fmsInteriorDetailDTOS) ) {
                                            for (FmsInteriorDetailDTO fmsInteriorDetailDTO : fmsInteriorDetailDTOS) {
                                                String key = fmsInteriorDetailDTO.getBusDetailId() + "_" + fmsInteriorDetailDTO.getInnerAccountId() + "_" + fmsInteriorDetailDTO.getCollectType();
                                                existKey.add(key);
                                            }
                                        }
                                        List<LdgBillAccountDTO> ldgBillAccountDTOs = CollUtil.newArrayList();
                                        for (LdgBillAccountDTO ldgBillAccountDTO : ldgBillAccountDTOList) {
                                            String key = ldgBillAccountDTO.getId() + "_" + ldgBillAccountDTO.getSubtractInnerAccountId() + "_" + FmsEnumType.INNER_BUSINESS_TYPE.INNER_BUSINESS_TYPE_062.getCode();
                                            if ( existKey.contains(key) ) {
                                                logger.info("票据id: {}, 内部户id: {} 已经存在明细账.", ldgBillAccountDTO.getId(), ldgBillAccountDTO.getSubtractInnerAccountId());
                                                continue;
                                            }
                                            ldgBillAccountDTOs.add(ldgBillAccountDTO);
                                        }
                                        if ( CollUtil.isEmpty(ldgBillAccountDTOs) ) {
                                            continue;
                                        }

                                                    // 根据集团生成内部户明细账
                                                    List<String> unCreateList = new ArrayList<>();
                                                    Map<String, FmsInnerAccountEntity> innerAccountMap = getInnerMapByDetail(ldgBillAccountDTOs, unCreateList,true);
                                                    List<LdgBillAccountDTO> updateList = this.generateInteriorDetail(ldgBillAccountDTOs, innerAccountMap, unCreateList, innerIdList,
                                                            FmsEnumType.INNER_BUSINESS_TYPE.INNER_BUSINESS_TYPE_062.getCode(),true, date);

                                                    if (CollUtil.isNotEmpty(updateList)) {
                                                        for (LdgBillAccountDTO dto : updateList) {
                                                            updateBillMap.put(dto.getId(), dto);
                                                        }
                                                    }

                                                                for (LdgBillAccountDTO dto : ldgBillAccountDTOs) {
                                                                    LdgBillAccountDTO ldgBillAccount = ObjUtil.newInstance(LdgBillAccountDTO.class);
                                                                    ldgBillAccount.setId(dto.getId());
                                                                    ldgBillAccount.setRecipientReceiptStatus(RECIPIENT_RECEIPT_STATUS_TYPE);
                                                                    LdgBillAccountDTO updateBill = updateBillMap.get(dto.getId());
                                                                    if (ObjUtil.isEmpty(updateBill)) {
                                                                        ldgBillAccount.setHasSubtractAmount(dto.getHasSubtractAmount());
                                                                    } else {
                                                                        ldgBillAccount.setHasSubtractAmount(Constant.DEFAULT_FLAG_Y);
                                                                    }
                                                                    ldgBillAccountList.add(ldgBillAccount);
                                                                }
                                                            }

                                                            if ( CollUtil.isNotEmpty(ldgBillAccountList) ) {
                                                                //批量将一批数据中对方签收状态更新为“对方已签收”
                                                                coreLdgClient.updateRecipientReceiptStatusByIdList(ldgBillAccountList);
                                                            }
                                                        }
