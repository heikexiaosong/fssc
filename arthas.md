watch java.util.Map computeIfAbsent 'target' -x 2

watch cn.ztessc.service.LdgCommonService calcFormula '{params}' "params.length == 3" -b -x 2

watch java.util.Map computeIfAbsent 'target' -x 2

watch cn.ztessc.service.LdgCommonService#calcFormul '{params}'  "params.length == 3" -b -x 2


watch cn.ztessc.util.InterestPlanGenerator generateInterestPlanDates "{params, returnObj}" -b -x 3


watch cn.ztessc.service.inneraccount.FmsInnerBalanceService calculateAmount "{params[1][0]}" -b -x 2


watch cn.ztessc.common.service.BaseService updateBatch "{params}" -b -x 3


### 推包
mvn deploy:deploy-file -Dfile=target/zfs-portal-core.jar -Dversion=4.1.2.6.1 -DgroupId=cn.ztessc -DartifactId=zfs-portal-core -Dpackaging=jar  -Durl=http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/ -DrepositoryId=public

mvn deploy:deploy-file -Dfile=pom.xml -Dversion=4.1.2.6.1 -DgroupId=cn.ztessc -DartifactId=zfs-portal-core -Dpackaging=pom  -Durl=http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/ -DrepositoryId=public
