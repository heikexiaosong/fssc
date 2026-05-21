### 生成还息计划

通过放款ID生成还息计划
- http://127.0.0.1/sys/fms/fmsProvideRepayPlan/generateRepaymentPlan/332fa8f203147c5605913139322e56a1
- cn.ztessc.controller.provide.FmsProvideRepayPlanController#generateRepaymentPlan

```sql
-- 放款记录（融资贷款）
-- provideLoanEntity
select * from FMS_PROVIDE_LOAN where id = '332fa8f203147c5605913139322e56a1' and enabled_flag = '0'

-- 贷款记录  id = FMS_PROVIDE_LOAN::PROVIDE_HEADER_ID
-- fmsProvideHeaderEntity
select * from FMS_PROVIDE_HEADER where id =  '7138bbd96d42449e023d3139322e5f34' and enabled_flag = '0'


-- 查询还本计划
-- repayPlanEntityList
select * from FMS_PROVIDE_REPAY_PLAN
         where REPAY_TYPE = '1'  -- 1: 还本； 2： 还息； 3： 还本息
           and LOAN_ID = '332fa8f203147c5605913139322e56a1'
           and PROVIDE_ID = '7138bbd96d42449e023d3139322e5f34'

-- 查询还息计划
-- interestPlanEntityList
select * from FMS_PROVIDE_REPAY_PLAN
         where REPAY_TYPE = '2'  -- 1: 还本； 2： 还息； 3： 还本息
           and LOAN_ID = '332fa8f203147c5605913139322e56a1'
           and PROVIDE_ID = '7138bbd96d42449e023d3139322e5f34'

-- 查询放款关联的利率
-- rateList
select * from FMS_LOAN_INTEREST_RECORD
         where 1=1
           and PROVIDE_LOAN_ID = '332fa8f203147c5605913139322e56a1'
           and PROVIDE_HEADER_ID = '7138bbd96d42449e023d3139322e5f34'

```
