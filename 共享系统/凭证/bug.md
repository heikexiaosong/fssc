# https://www.tapd.cn/tapd_fe/30291203/bug/detail/1130291203001474122
- 【沈阳生产内网】辅助核算支出合同点击后没有值（辅助会计结构设置，由主数据切换为维度不生效）


URL: http://192.168.3.219/sys/base/accountsstructuresr/getSgdDtoMapBySobIdAndAccountIdList
PARAMS: {"accountIds": ["9a70116268e60a025b963132372e1f8a"], "sobIds": ["0836d624b00a423101db3132372e0782"], "groupId": "c485ffaaaaa934edcedc3132372e000b"}

```json
{
    "0836d624b00a423101db3132372e0782^c485ffaaaaa934edcedc3132372e000b^9a70116268e60a025b963132372e1f8a": [
        {
            "createTime": "2026-11-24 18:08:04",
            "id": "f381215f47fd23946dbe3132372e0292",
            "structureName": "部门",
            "structureCode": "002",
            "structureId": "e1425d818730102e59993132372e3cd3",
            "defaultCode": "",
            "defaultName": "",
            "isFill": "1",
            "fieldType": "table",
            "flexValueSetsId": "SYS_DEPT",
            "structureGroupId": "f381215f47fa6af6fc563132372e0291",
            "validityFlag": "0",
            "enabledFlag": "0",
            "createBy": "560a890ff9f9b7c6c7313132372e0825",
            "createDate": "2026-09-14 09:48:34",
            "lastUpdateBy": "560a890ff9f9b7c6c7313132372e0825",
            "lastUpdateDate": "2026-09-14 09:48:34",
            "groupId": "c485ffaaaaa934edcedc3132372e000b",
            "accountStructuresId": "e1425d818730102e59993132372e3cd3",
            "configType": "001",
            "dimensionType": "001",
            "sapCustomerFlag": "N",
            "csql": "SELECT\n    t1.code AS code,\n    t2.name AS name\nFROM\n    zfs_op.zfs_boe_header t,\n    zfs_sys.sys_dept t1,\n    zfs_sys.sys_dept_language t2\nWHERE\n        t.EMPLOYEE_DEPT_ID = t1.id\n  AND t1.id = t2.dept_id\n  AND t2.language = 'zh-CN'\n  AND t.id = @boeHeaderId",
            "dsql": "SELECT\n    t1.code AS code,\n    t2.name AS name\nFROM\n    zfs_op.zfs_boe_header t,\n    zfs_sys.sys_dept t1,\n    zfs_sys.sys_dept_language t2\nWHERE\n        t.EMPLOYEE_DEPT_ID = t1.id\n  AND t1.id = t2.dept_id\n  AND t2.language = 'zh-CN'\n  AND t.id = @boeHeaderId"
        }
    ],
    "\"0836d624b00a423101db3132372e0782^c485ffaaaaa934edcedc3132372e000b^9a70116268e60a025b963132372e1f8a\"": [
        {
            "createTime": "2026-11-24 18:08:04",
            "id": "f381215f47fd23946dbe3132372e0292",
            "structureName": "部门",
            "structureCode": "002",
            "structureId": "e1425d818730102e59993132372e3cd3",
            "defaultCode": "",
            "defaultName": "",
            "isFill": "1",
            "fieldType": "table",
            "flexValueSetsId": "SYS_DEPT",
            "structureGroupId": "f381215f47fa6af6fc563132372e0291",
            "validityFlag": "0",
            "enabledFlag": "0",
            "createBy": "560a890ff9f9b7c6c7313132372e0825",
            "createDate": "2026-09-14 09:48:34",
            "lastUpdateBy": "560a890ff9f9b7c6c7313132372e0825",
            "lastUpdateDate": "2026-09-14 09:48:34",
            "groupId": "c485ffaaaaa934edcedc3132372e000b",
            "accountStructuresId": "e1425d818730102e59993132372e3cd3",
            "configType": "001",
            "dimensionType": "001",
            "sapCustomerFlag": "N",
            "csql": "SELECT\n    t1.code AS code,\n    t2.name AS name\nFROM\n    zfs_op.zfs_boe_header t,\n    zfs_sys.sys_dept t1,\n    zfs_sys.sys_dept_language t2\nWHERE\n        t.EMPLOYEE_DEPT_ID = t1.id\n  AND t1.id = t2.dept_id\n  AND t2.language = 'zh-CN'\n  AND t.id = @boeHeaderId",
            "dsql": "SELECT\n    t1.code AS code,\n    t2.name AS name\nFROM\n    zfs_op.zfs_boe_header t,\n    zfs_sys.sys_dept t1,\n    zfs_sys.sys_dept_language t2\nWHERE\n        t.EMPLOYEE_DEPT_ID = t1.id\n  AND t1.id = t2.dept_id\n  AND t2.language = 'zh-CN'\n  AND t.id = @boeHeaderId"
        }
    ]
}

```

// cn.ztessc.service.voucher.base.AbstractVoucherCoreService#setLineDistDefault
// 001  SQL_CONFIG("001"), VISUAL_CONFIG("002");
configType： 
