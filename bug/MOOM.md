[root@iZgs001404vn2khpsgf1loZ logs]# jcmd 234173 GC.class_histogram | grep -E 'org.hibernate.engine.spi.EntityKey|org.hibernate.engine.internal.StatefulPersistenceContext'
  64:         38922         934128  org.hibernate.engine.spi.EntityKey
2165:             5            520  org.hibernate.engine.internal.StatefulPersistenceContext
4944:             5             80  org.hibernate.engine.internal.StatefulPersistenceContext$1
23013:             1             16  org.hibernate.engine.internal.StatefulPersistenceContext$$Lambda$2281/0x00007f84338df568
23014:             1             16  org.hibernate.engine.internal.StatefulPersistenceContext$$Lambda$2304/0x00007f84338eb750
