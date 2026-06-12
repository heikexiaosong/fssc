watch java.util.Map computeIfAbsent 'target' -x 2

watch cn.ztessc.service.LdgCommonService calcFormula '{params}' "params.length == 3" -b -x 2

watch java.util.Map computeIfAbsent 'target' -x 2

watch cn.ztessc.service.LdgCommonService#calcFormul '{params}'  "params.length == 3" -b -x 2


watch cn.ztessc.service.innerloan.FmsInnerLoanLedgerRecordService bulidLoanLedgerRecord "{params, returnObj}" -s -x 3


watch cn.ztessc.util.InterestCalculatorWithDate calculateEstimatedInterest "{params}" -b -s -x 3


watch cn.ztessc.common.service.BaseService updateBatch "{params}" -b -x 3


### 推包
mvn deploy:deploy-file -Dfile=target/zfs-op-server.jar -Dversion=4.1.2.6.7 -DgroupId=cn.ztessc -DartifactId=zfs-op-server -Dpackaging=jar  -Durl=http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/ -DrepositoryId=public

mvn deploy:deploy-file -Dfile=pom.xml -Dversion=4.1.2.6.1 -DgroupId=cn.ztessc -DartifactId=zfs-portal-core -Dpackaging=pom  -Durl=http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/ -DrepositoryId=public





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
