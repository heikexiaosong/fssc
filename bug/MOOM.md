【应收票据支付没有自动带出子票起和止，且未开启拆分票据的逻辑，但是在确认支付以后自动更新母票的票据状态=已拆分】
https://www.tapd.cn/tapd_fe/58660940/bug/detail/1158660940001469692


http://127.0.0.1/sys/fms/fmspaymentunder/payBillSuccess

{
  "id": "49ddae5a-968f-428c-ac29-f0476c6500a1",
  "businessTypePay": "OFFLINE_PAYMENT_WORKBENCH",
  "paymentModeCode": "4",
  "billAccountList": [{"id":"65cdac01aac3ea68fd3831302e381594","boeId":"49ddae5a-968f-428c-ac29-f0476c6500a1","billId":"65cdac01aac3ea68fd3831302e381594","ticketAmount":9000,"busAmount":500,"billApplyAmount":500,"availableAmount":8500,"splitFlag":"Y","splitFlagName":"是","subticketStart":"000000000001","subticketStartInt":1,"subticketEnd":"000000900000","subticketEndInt":900000,"ecdsMonoblock":"","ecdsMonoblockName":null,"disposeType":"1","disposeTypeName":"线下","busDate":null,"remark":null,"validityFlag":"0","enabledFlag":"0","createBy":"-1","createDate":"2026-11-10 10:55:00","lastUpdateBy":"-1","lastUpdateDate":"2026-11-10 10:55:00","groupId":"accba517-5725-4fae-8938-48946676bfc1","archiveFlag":null,"archiveDate":null,"postalOrderCode":"2026111001","billType":"002","billTypeName":"商业承兑汇票","fmsAccountId":"a9262d0e688ea632f242b1b0aeb55b55","fmsAccountNum":"0200002909200098212","fmsAccountCode":"0200002909200098212","fmsAccountCodeName":"钢研昊普科技有限公司","ticketDate":"2026-10-30","billExpireDate":"2026-11-30","billStatus":"001","billStatusName":"签收","receiveVendorId":null,"receiveVendorName":"钢研昊普科技有限公司","extendS1":null,"extendS2":null,"extendS3":null,"companyBankName":"钢研昊普科技有限公司","pledgeAmountStart":1,"pledgeAmountEnd":50000,"oriBillId":"65cdac01aac3ea68fd3831302e381594","oriBillCode":"2026111001","fmsDataSource":"1","leId":null,"lineSort":1,"circulateFlag":"02","circulateFlagName":"可流通","issueBillApplyNo":null,"applyNoEdit":"Y","_rowIndex":1}],
  "actualPaymentAmount": "500",
}


ENABLE_BILL_BUS_MANAGE: N
AUTO_GENERATE_INVOICE: N
