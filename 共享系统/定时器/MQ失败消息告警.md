# MQ失败消息告警
- 获取当前失败消息统计信息，存在失败消息则发送告警邮件。
- mqsTask.alertMqFailedMsg
```python
# 查询最近1天内失败的消息数
db.getSiblingDB("ztefssc").getCollection("zfs_data_push_send").find({pushCount: { $gte: 10 }, updateTime: { $gte: ISODate("2026-11-11T14:49:00.415Z") }})



# 查询未消费消息总数
long countMsg = getInstance().countAllMsg();
String content = "最近24h内存在【" + countFailedMsg + "】笔失败消息，"
        + "截止目前未消费消息记录数【" + countMsg + "】，"
        + "<font color=\"red\">存在积压大量单据风险，请尽快安排处理</font>。<br/><br/>"
        + "详细信息请前往【运维监控系统-操作工具-MQ消息监控】查看。";

SysWaitSendMessageDTO sysWaitSendMessageDTO = ObjUtil.newInstance(SysWaitSendMessageDTO.class);
sysWaitSendMessageDTO.setReceiver(emailAddresses);
sysWaitSendMessageDTO.setSubject("【MQ消费失败告警】存在失败消息，请联系运维人员");
sysWaitSendMessageDTO.setContent(content);

# [POST][JSON] sendNotificationMessage/sendEmailByEmailAddr
interfaceClient.sendEmailByEmailAddr(sysWaitSendMessageDTO);

```
