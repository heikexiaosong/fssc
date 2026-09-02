watch java.util.Map computeIfAbsent 'target' -x 2

watch cn.ztessc.service.LdgCommonService calcFormula '{params}' "params.length == 3" -b -x 2

watch java.util.Map computeIfAbsent 'target' -x 2

watch cn.ztessc.service.provide.FmsBoeLedgerHandleService buildFmsBaseData '{params}'   -b -x 2


watch cn.ztessc.service.pool.FmsPoolReorgService queryAllSubLeIdByLeId "{params, returnObj}" -s -x 3


watch cn.ztessc.boecommon.service.base.AbstractBoeFormCoreServiceImpl setValueFromBoeForm "{params}" -b -s -x 3


watch cn.ztessc.route.BankRouteHandle sendRequestNew "{params[0], returnObj}" -s -x 3

watch cn.ztessc.service.collateral.FmsCollateralManageService queryList "{params, returnObj}" -s -x 3


### 推包
mvn deploy:deploy-file -Dfile=target/zfs-op-server.jar -Dversion=4.1.2.6.7 -DgroupId=cn.ztessc -DartifactId=zfs-op-server -Dpackaging=jar  -Durl=http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/ -DrepositoryId=public

mvn deploy:deploy-file -Dfile=pom.xml -Dversion=4.1.2.6.1 -DgroupId=cn.ztessc -DartifactId=zfs-portal-core -Dpackaging=pom  -Durl=http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/ -DrepositoryId=public


# 强制让JVM做一次采样并统计特定类的数量
vmtool --action getInstances --className org.hibernate.engine.spi.EntityKey --limit 10

# 假设你的 Java 进程号是 12345
jcmd 234173 GC.class_histogram | grep org.hibernate.engine.spi.EntityKey


watch cn.ztessc.route.BankRouteHandle todayAccountBlance "returnObj.size()" -n 1000 -x 2



watch cn.ztessc.common.service.SqlService page "{params[0]}" -s -x 3



@ArrayList[
    @FmsInnerLoanInterestRecordEntity[
        serialVersionUID=@Long[1],
        id=@String[631bd9b4501ffd499f283139322e7835],
        loanId=@String[42b9bdc20762246daf6b3139322e25ff],
        currencyCode=@String[CNY],
        interestType=@String[0],
        loanAmount=@BigDecimal[48000000.00],
        rate=@BigDecimal[3.1000],
        beginDate=@DateTime[2026-06-17 00:00:00,000],
        endDate=@Timestamp[2028-12-17 00:00:00,000],
        groupId=@String[ba2ad44f9aba2ad54fa43139322e0000],
        archiveFlag=null,
        archiveDate=null,
        extendS1=null,
        extendN1=null,
        extendD1=null,
        loanRecordId=@String[ec36d2b3d0b94a6920c93139322e06d4],
        repayPrincipalPartial=@BigDecimal[0],
        validityFlag=@String[0],
        enabledFlag=@String[0],
        createBy=@String[93ec6982a636d4515b8f3139322e421d],
        createDate=@Date[2026-06-12 17:16:37,180],
        lastUpdateBy=@String[93ec6982a636d4515b8f3139322e421d],
        lastUpdateDate=@DateTime[2026-06-12 17:16:37,180],
    ],
    @FmsInnerLoanInterestRecordEntity[
        serialVersionUID=@Long[1],
        id=@String[631bd9b4501ffd3748303139322e7834],
        loanId=@String[42b9bdc20762246daf6b3139322e25ff],
        currencyCode=@String[CNY],
        interestType=@String[0],
        loanAmount=@BigDecimal[49000000.00],
        rate=@BigDecimal[3.1000],
        beginDate=@Timestamp[2026-03-17 00:00:00,000],
        endDate=@DateTime[2026-06-17 00:00:00,000],
        groupId=@String[ba2ad44f9aba2ad54fa43139322e0000],
        archiveFlag=null,
        archiveDate=null,
        extendS1=null,
        extendN1=null,
        extendD1=null,
        loanRecordId=@String[ec36d2b3d0b94a6920c93139322e06d4],
        repayPrincipalPartial=@BigDecimal[1000000],
        validityFlag=@String[0],
        enabledFlag=@String[0],
        createBy=@String[93ec6982a636d4515b8f3139322e421d],
        createDate=@Date[2026-06-12 17:16:37,179],
        lastUpdateBy=@String[93ec6982a636d4515b8f3139322e421d],
        lastUpdateDate=@DateTime[2026-06-12 17:16:37,180],
    ],
]




```sql
select *
from evs_appraise_tasks i
	join eid_image_infos e on e.image_number = i.image_num and e.image_status in ('HEAVY_AND', 'COMPLEMENT') and i.TASK_STATUS = 'NONCOMPLETE'

where e.enabled = 'Y'
	and i.enabled = 'Y'
	and e.image_number like CONCAT('%', 'TYBZ260617004797', '%')
	
	join (
		select eah.image_info_id, eah.appraise_date, CONCAT(eul.real_name, ees.login_name) as appraise_employee
			, ees.login_name as appraise_employee_number, eul.real_name as appraise_employee_name, eah.appraise_node, eah.appraise_type, eah.GROUP_ID
			, eah.appraise_desc
		from (
			select eah_inner.IMAGE_INFO_ID, max(eah_inner.appraise_date) as max_appraise_date
			from evs_appraise_histories eah_inner
			group by IMAGE_INFO_ID
		) eahm
			left join evs_appraise_histories eah
			on eah.IMAGE_INFO_ID = eahm.IMAGE_INFO_ID
				and eah.appraise_date = eahm.max_appraise_date
			left join evs_users ees on eah.appraise_employee_id = ees.id
			left join evs_users_language eul
			on ees.id = eul.user_id
				and eul.language = 'zh-CN'
	) appr on e.id = appr.image_info_id and appr.GROUP_ID = i.GROUP_ID
where e.enabled = 'Y'
	and i.enabled = 'Y'
	and e.image_number like CONCAT('%', 'TYBZ260617004797', '%')
	and e.image_number not in ('-9')
	and (e.upload_employee_id = '61472f4ad60c0e001ee23139322e78c5'
		or exists (
			select 1
			from evs_front_users efu
				inner join evs_front_users_company efuc on efu.employee_id = efuc.employee_id
			where efu.group_id = e.group_id
				and efuc.company_id = e.company_id
				and efu.employee_id = '61472f4ad60c0e001ee23139322e78c5'
				and efu.upload_group = '2_FINANCIAL_GROUP'
				and efu.enabled = 'Y'
		))

```


kubectl --kubeconfig=/home/fssc/k8s/cd-prd-config cp skxtprd/zfs-fms-server-86cf7cf569-z4sh7:/home/fssc/logs/zfs-fms_elk/run.log ./run.log

kubectl --kubeconfig=/home/fssc/k8s/cd-prd-config logs -f --tail=300 -n skxtprd pod/zfs-fms-server-86cf7cf569-z4sh7



update fms_payment_apply_header set external_system_key = '00b6fc3f886e5d23f04d3137322e096d', old_sys_key = '0ba080d84f4a0d783518b1b7b2ae0c6d', last_update_date = TIMESTAMP '2026-07-23 09:55:32.598', last_update_by = '98d8b480ffff9e2afb9516ffb1b7b2ae207b' where id = '93c2b05c-1087-49db-81c6-a8bfb9868c61';

update fms_payment_instructions set payment_status = '40', external_system_key = '00b6fc3f886e5d23f04d3137322e096d', repeat_send_count = 3, last_update_date = TIMESTAMP '2026-07-23 09:55:32.603', last_update_by = '98d8b480ffff9e2afb9516ffb1b7b2ae207b' where boe_no = 'HUASHI-AP2607170041' and payment_status in ('12', '24')


INSERT INTO fms_payment_instructions (ID,PAYMENT_STATUS,DATA_SOURCE,INSTRUCTION_NO,CREATE_DATE,BOE_NO,EXTERNAL_SYSTEM_KEY,REPEAT_SEND_COUNT,LAST_UPDATE_DATE) VALUES
	 ('93c2b05c-1087-49db-81c6-a8bfb9868c62','40','3','212607230123','2026-07-17 13:30:25','HUASHI-AP2607170041','0ba080d84f4a0d783518b1b7b2ae0c6d',2,'2026-07-22 16:18:42');



 INSERT INTO fms_payment_instructions (ID,INSTRUCTION_NO,BOE_NO,LE_ID,PAYMENT_CURRENCY,PAYMENT_MODE_CODE,PAYER_BANK_ACCOUNT_NAME,PAYER_BANK_ACCOUNT_NUM,PAYER_BANK_HEAD_CODE,PAYER_BANK_HEAD_NAME,PAYER_BANK_BRANCH_NAME,GATHER_BANK_ACCOUNT_NAME,GATHER_BANK_ACCOUNT_NUM,GATHER_BANK_HEAD_CODE,GATHER_BANK_HEAD_NAME,GATHER_BANK_BRANCH_NAME,SITES_TYPE,EXPECT_PAYMENT_DATE,URGENT_SIGN,TRAD_PURPOSE,BOE_ABSTRACT,INSTRUCTION_TYPE,STRIDE_BANK_SIGN,STRIDE_AREA_SIGN,PAYMENT_STATUS,SEND_STATUS,HANG_STATUS,ARCHIVE_FLAG,ARCHIVE_DATE,VALIDITY_FLAG,ENABLED_FLAG,CREATE_BY,CREATE_DATE,LAST_UPDATE_BY,LAST_UPDATE_DATE,GROUP_ID,EXTEND_NUM,EXTEND_DATE,EXTERNAL_SYSTEM_KEY,PAYER_BANK_ACCOUNT_ID,DATA_SOURCE,BATCH_NUMBER,FAIL_REASON,TRANSACTION_TIME,INSTRUCTION_SEND_TIME,BOE_ID,OPERATOR_ID,PAYMENT_DATE,REGISTRA_TIME,FIRST_BOE_DATE,PAYMENT_REMARK,PAYER_UNITED_CODE,GATHER_UNITED_CODE,REPEAT_SEND_COUNT,DATA_SIGN_KEY,QUERY_COUNT,RELA_BILL_NO,RELA_BILL_ID,RETURN_NUMBER,RETURN_NUMBER_CODE,APPLY_AMOUNT,CA_PLAIN_DATA,CA_SIGNED_DATA,CA_AUTH_FLAG,SOURCE_BOE_NO,E_BANK_MONOBLOCK,PAYER_LE_ID,GATHER_LE_ID,ACTUAL_AMOUNT,EXTEND_S2,EXTEND_S3,EXTEND_S4,EXTEND_S5,LAST_APPROVE_CODE,PRE_CA_FLAG,CITY_PLACES,WHETHER_LANDING,WHETHER_BANK,BIG_SMALL_PAY,FLOW_RELATED_FLAG,OPERATION_TYPE_ID,SEND_FILE,ORI_FILE,FILE_MD,VCH_ID,BUSINESS_TYPE,PAY_FAIL_REASON,QUERY_FAIL_REASON,PUSH_STATUS,BUSINESS_NO_SK,EMPLOYEE_CODE,OPERATION_TYPE_BOE_ID,ACTUAL_PAYMENT_STANDARD_CURRENCY_RATE,ACTUAL_PAYMENT_STANDARD_CURRENCY_AMOUNT,ACTUAL_PAYMENT_AMOUNT,ACTUAL_PAYMENT_CURRENCY,ACTUAL_PAYMENT_ACCOUNT,BAT_PSCPT,SK_CODE,POOL_FLAG,WRITE_FLAG,INS_EXCE_REQUESTER,INS_EXCE_REQ_DATE,INS_EXCE_REQ_STATUS,REVIEW_STATUS,REVIEWER,REVIEW_DATE,CONFIRM_FAIL_REASON,SUSPICIOUS_FLAG,SUSPICIOUS_REASON,OLD_PAYMENT_CODE,OLD_PAYMENT_NAME,TRANS_MSG,INTEREST_REFUNDED,EXTEND_STR,VIRTUAL_ACCOUNT_NUMBER_SECOND,ORIGINAL_TRANSACTION_SET_NUMBER,ORIGINAL_TRANSACTION_SERIAL_NUMBER,CUSTOMER_SUMMARY,BOE_TYPE_CODE,LOGIN_USER_NAME,CREDIT_PAYMENT,BUSINESS_CONTRACT_NUMBER,UNIQUE_BUS_ID,BANK_BIZ_ID,LINE_ID,SOURCE_INSTRUCTIONS_ID,HANG_REASON,BALANCE_AMOUNT_DETAIL,AUTH_PAYMENT_FLAG,MATCH_COUNT,DH_SEQ_NO,OPPOSITE) VALUES

	 ('93c2b05c-1087-49db-81c6-a8bfb9868c62','212607230123','HUASHI-AP2607230168','c527d7a1ffffac182610cb4ab1b7b2ae5818','CNY','1','四川省第十五建筑有限公司','1051300000629139','BCD','成都银行','成都银行南充分行','四川瑞泰通建筑工程有限公司','2315532409100064963','102','中国工商银行','中国工商银行股份有限公司四川省南充火车站支行','1','2026-07-17 00:00:00','0','姜家拐项目付劳务费','姜家拐项目付劳务费','1','0','1','40','5',NULL,NULL,NULL,'0','0','3661c55670367c4704bf3137322e0007','2026-07-17 13:30:25','-1','2026-07-23 09:55:33','af4ce58b58c721b9dcde3137322e0004',0.00,NULL,'0ba080d84f4a0d783518b1b7b2ae0c6d','3337afa5ffffebd4ab8e4729b1b7b2ae15d0','3',NULL,'支取金额超过可用余额',NULL,'2026-07-20 11:38:02','93c2b05c-1087-49db-81c6-a8bfb9868c61',NULL,NULL,NULL,'2026-07-20 10:36:11',NULL,'313673060906','102673053249',3,'eb921b0893aef767c9fce3ddc3ce87fb02597822e35f7dafc6d2bf20794689eb',2,NULL,NULL,NULL,NULL,1993777.47,NULL,NULL,NULL,'HUASHI-BX2607088269',NULL,'c527d7a1ffffac182610cb4ab1b7b2ae5818',NULL,NULL,NULL,NULL,NULL,NULL,'HX00068657&胡昱',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'PAYMENT_REQUISITION_BOE',NULL,'支取金额超过可用余额',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'S',NULL,NULL,NULL,'01',NULL,NULL,NULL,NULL,NULL,'HSJS001','一般户转账付款',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'PAYMENT_BOE',NULL,NULL,NULL,NULL,NULL,'93c2b05c-1087-49db-81c6-a8bfb9868c61',NULL,NULL,'5128192.81 | 2026-07-20 | 正常',NULL,NULL,NULL,'四川瑞泰通建筑工程有限公司');
