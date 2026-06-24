# 生成编码
- coderule/generateCode/{ruleNum}/{groupId}
- 

```sql
select *
  from SYS_CODE_RULE
 where RULE_NUM = {ruleNum}
   and GROUP_ID = {groupId}


-- 
select *
  from SYS_CODE_RULE_DETAIL
 where RULE_ID = 'GHG-ebecdd89-0938-326f-e055-0042aea2f86e' -- SYS_CODE_RULE::ID
 order by SORT_NO;

-- 
select *
  from SYS_CODE_RULE_MODEL
 where id in (null, 'GHG-XY000010', 'GHG-57ed1ce6-7e7f-4ab4-b046-8ea6370eb908') -- SYS_CODE_RULE_DETAIL::MODEL_ID

-- 
select *
  from SYS_CODE_RULE_TYPE
 where id in ('GHG-4d405ff6-89ff-4eeb-b251-3dde80c61eb0', 'GHG-fdd14288-8bcd-49eb-a3fe-6677e79c20e5', 'GHG-2e91355f-78f4-4b72-8ad6-0417ababc36a') -- SYS_CODE_RULE_DETAIL::TYPE_ID
```


方法：sysCodeRuleGenerateService::modelEntity.getServiceName() + "Batch"
参数：
  "pro_{ruleNum}_{SYS_CODE_RULE_DETAIL::ID}",
	num,
											detailDTO.getInitValue(),
											detailDTO.getStep(),
											detailDTO.getLength()

例如：
固定值,  AP
系统时间,取系统日期(6位)
序列值,  按天取序列

3种类型编码拼接结果: AP2606180411
