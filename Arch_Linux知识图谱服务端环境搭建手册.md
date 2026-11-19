---
date: 2026-11-13
subtitle: Neo4j + PostgreSQL + Graph API + MCP
title: Arch Linux 知识图谱服务端环境搭建手册
---

# 1. 文档目标

本文档只整理 **Arch Linux 服务端环境搭建**，不包含 Windows Java
源码分析器的开发细节。

目标是在 Arch Linux 上部署一套可长期运行的知识图谱服务，为 Windows
源码分析器、Zed、Codex 等客户端提供数据血缘图谱存储和查询能力。

推荐职责划分：

  ------------------------------------------------------------------------------------------
  组件                                职责
  ----------------------------------- ------------------------------------------------------
  Neo4j Community                     保存 Project、Class、Method、SQL、Table、Column
                                      及血缘关系

  PostgreSQL                          保存扫描任务、导入批次、解析证据和运行记录

  Graph API                           接收 Windows 分析器上传的 JSON，校验并写入图数据库

  MCP Server                          为 Zed、Codex 等 AI 客户端提供受控图谱查询工具

  OpenMetadata                        复用现有部署，管理数据库元数据并展示确认后的数据血缘
  ------------------------------------------------------------------------------------------

> 重要：现有机器已经运行 OpenMetadata 2.0.2，且此前 Docker
> 数据和根分区空间比较紧张。本手册以"新增服务、不破坏现有容器和卷"为原则。所有清理、迁移和
> Docker 卷操作都应先备份并核对，不能执行
> `docker system prune --volumes` 之类的清理命令。

# 2. 部署前检查

## 2.1 检查系统、磁盘和内存

``` bash
uname -a
cat /etc/os-release
lscpu
free -h
df -hT
lsblk -f
```

重点确认：

-   Neo4j 数据目录所在分区有足够空间。
-   Docker 当前数据目录是否仍在根分区。
-   机器内存是否足够同时运行现有 OpenMetadata、Neo4j、PostgreSQL 和
    MCP。
-   是否有独立数据分区可用于持久化图数据库。

此前这台机器的根分区约 32GB，曾出现剩余空间不足的情况。**不要默认把
Neo4j 数据放在 `/var/lib/docker` 或根分区。**

## 2.2 检查现有 Docker 服务

``` bash
docker ps -a
docker images
docker volume ls
docker system df
docker info --format '{{.DockerRootDir}}'
```

记录现有 OpenMetadata 容器、镜像、端口、卷和数据目录。不要删除或重建现有
OpenMetadata 容器。

## 2.3 检查端口占用

``` bash
sudo ss -lntup
```

建议规划如下端口：

  服务                       端口 访问范围
  ------------------------ ------ ------------------------------------
  OpenMetadata UI/API        8585 沿用现有配置
  OpenMetadata ingestion     8080 沿用现有配置，按实际情况
  Neo4j Browser              7474 管理端访问
  Neo4j Bolt                 7687 Graph API/MCP 所在主机或可信客户端
  Graph API                  8090 Windows 分析器访问
  MCP HTTP                   8091 Zed/Codex 访问
  PostgreSQL                 5432 默认仅本机/容器内部访问

端口仅为规划值。部署前必须以 `ss` 和现有 Compose 文件为准，避免冲突。

# 3. 目录规划与磁盘准备

## 3.1 推荐目录

``` text
/opt/knowledge-graph/
├── compose.yaml
├── .env
├── graph-api/
├── mcp-server/
├── config/
└── logs/

/srv/knowledge-graph/
├── neo4j/
│   ├── data/
│   ├── logs/
│   └── import/
└── postgres/
    └── data/
```

`/opt` 放部署配置和程序；`/srv` 放持久化数据。若 `/srv`
不在独立大容量分区，应先确认其实际挂载位置。

## 3.2 创建目录

``` bash
sudo mkdir -p /opt/knowledge-graph/{graph-api,mcp-server,config,logs}
sudo mkdir -p /srv/knowledge-graph/neo4j/{data,logs,import}
sudo mkdir -p /srv/knowledge-graph/postgres/data

df -hT /opt /srv
```

先不要盲目 `chown` 数据目录。容器运行 UID/GID
应以所选镜像实际配置为准；确认后再调整目录权限。

## 3.3 容量建议

对于 16GB 内存、约 120GB SSD 的旧机器，建议先小规模试点：

-   Neo4j：Heap 512MB～1GB，Page Cache 512MB～1GB 起步。
-   PostgreSQL：小规模连接数和较保守的内存配置。
-   Graph API、MCP：按实际负载限制工作进程数。
-   给现有 OpenMetadata 留出内存和磁盘余量。

以上只是初始值，不是最终性能配置。需观察
`free -h`、`docker stats`、磁盘使用率和 Neo4j 日志后调整。

# 4. Docker 环境确认

如果 Docker 已正常运行，跳过安装，不要重复安装或重置 Docker 数据目录。

``` bash
systemctl status docker
docker version
docker compose version
```

若 Docker 尚未安装：

``` bash
sudo pacman -Syu
sudo pacman -S docker docker-compose
sudo systemctl enable --now docker
```

将当前用户加入 docker 组属于授予近似 root 的权限，只有明确需要时才执行：

``` bash
sudo usermod -aG docker "$USER"
```

执行后需要重新登录。也可以始终使用 `sudo docker`。

# 5. Neo4j 部署

## 5.1 镜像版本策略

生产环境不要长期使用浮动标签。先确认目标 Neo4j 版本与当前
Docker、JDK、插件及数据格式兼容，再固定具体版本或 digest。

以下 Compose 片段使用 `neo4j:5-community`
作为示例占位。正式部署前应将其替换为经过验证的明确版本标签。

## 5.2 创建环境变量文件

``` bash
cd /opt/knowledge-graph
sudo install -m 600 /dev/null .env
sudo nano .env
```

内容：

``` dotenv
NEO4J_PASSWORD=请替换为足够长的随机强密码
```

不要把 `.env` 提交到 Git，也不要把真实密码粘贴到聊天或日志中。

## 5.3 Neo4j Compose 示例

推荐先让 Neo4j 只监听本机回环地址，由同机 Graph API/MCP 访问。这样无需将
Bolt 暴露给局域网。

创建 `/opt/knowledge-graph/compose.yaml`：

``` yaml
services:
  neo4j:
    image: neo4j:5-community
    container_name: kg-neo4j
    restart: unless-stopped

    environment:
      NEO4J_AUTH: "neo4j/${NEO4J_PASSWORD}"
      NEO4J_server_memory_heap_initial__size: 512m
      NEO4J_server_memory_heap_max__size: 1G
      NEO4J_server_memory_pagecache_size: 512m

    ports:
      - "127.0.0.1:7474:7474"
      - "127.0.0.1:7687:7687"

    volumes:
      - /srv/knowledge-graph/neo4j/data:/data
      - /srv/knowledge-graph/neo4j/logs:/logs
      - /srv/knowledge-graph/neo4j/import:/import

    healthcheck:
      test:
        - CMD-SHELL
        - 'cypher-shell -u neo4j -p "$${NEO4J_PASSWORD}" "RETURN 1"'
      interval: 30s
      timeout: 10s
      retries: 5
```

检查 Compose 配置：

``` bash
cd /opt/knowledge-graph
sudo docker compose config
```

启动 Neo4j：

``` bash
sudo docker compose up -d neo4j
sudo docker ps
sudo docker logs --tail 100 kg-neo4j
```

验证本机 Browser 端口：

``` bash
curl -I http://127.0.0.1:7474
```

如果 Graph API/MCP 也运行在同一个 Compose 网络中，应让它们通过服务名
`neo4j:7687` 连接，不必将 Bolt 映射到宿主机。

如果后续确实需要 Windows 直接连接 Bolt，再考虑将 `7687` 绑定到 Arch
的固定局域网 IP，并通过防火墙限制来源 IP。不要使用 `0.0.0.0:7687`
无限制暴露。

## 5.4 登录 Neo4j

``` bash
sudo docker exec -it kg-neo4j cypher-shell -u neo4j
```

按提示输入密码。首次登录后按 Neo4j 要求修改初始密码。

## 5.5 初始化约束

在 `cypher-shell` 中执行：

``` cypher
CREATE CONSTRAINT table_fqn IF NOT EXISTS
FOR (n:Table) REQUIRE n.fqn IS UNIQUE;

CREATE CONSTRAINT column_fqn IF NOT EXISTS
FOR (n:Column) REQUIRE n.fqn IS UNIQUE;

CREATE CONSTRAINT class_id IF NOT EXISTS
FOR (n:Class) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT method_id IF NOT EXISTS
FOR (n:Method) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT project_id IF NOT EXISTS
FOR (n:Project) REQUIRE n.id IS UNIQUE;
```

测试写入：

``` cypher
MERGE (p:Project {id: 'zfs-fms'})
SET p.name = 'zfs-fms';

MERGE (t:Table {fqn: 'finance_db.fms.FMS_ACCOUNT'})
SET t.name = 'FMS_ACCOUNT';

MATCH (p:Project {id: 'zfs-fms'})
MATCH (t:Table {fqn: 'finance_db.fms.FMS_ACCOUNT'})
MERGE (p)-[:USES_TABLE]->(t);

MATCH (p:Project)-[r]->(t:Table)
RETURN p, r, t;
```

表标识应使用稳定的全限定名，建议包含数据库、Schema
和表名，不能只使用表名。

# 6. PostgreSQL 部署选择

先确认现有 PostgreSQL 是否可以复用：

``` bash
psql --version
systemctl status postgresql
docker ps --format '{{.Names}} {{.Ports}}'
```

如果现有 PostgreSQL 已经稳定运行，建议为知识图谱创建独立数据库或独立
Schema，不要重复部署一套数据库。

例如（根据现有实例和权限调整）：

``` sql
CREATE DATABASE knowledge_graph;
```

如果需要独立 Docker
PostgreSQL，应先确定版本、备份策略、数据目录权限和资源预算，再加入
Compose。不要在未确认现有 PostgreSQL
角色、数据库和端口的情况下覆盖配置。

建议 PostgreSQL 主要保存：

-   扫描任务和批次状态。
-   Git commit、分支和分析器版本。
-   解析失败记录。
-   原始证据和人工确认记录。
-   OpenMetadata 同步任务及结果。

图节点和路径关系以 Neo4j 为主，避免两边维护两份互相冲突的关系数据。

# 7. Graph API 与 MCP 服务部署

## 7.1 服务职责

Graph API（建议端口 8090）：

-   接收 Windows 分析器提交的标准 JSON。
-   验证项目、扫描批次、数据结构和访问令牌。
-   对节点和关系进行稳定 ID 去重。
-   将数据批量写入 Neo4j。
-   记录扫描任务、解析异常和导入结果。

MCP Server（建议端口 8091）：

-   搜索表和字段。
-   查询表被哪些 Java 方法使用。
-   查询方法读写了哪些表。
-   查询表级、字段级血缘。
-   查询方法调用链。
-   分析表或字段变更影响范围。
-   返回源文件路径、行号、Git commit 和证据。

## 7.2 建议目录

``` text
/opt/knowledge-graph/
├── compose.yaml
├── .env
├── graph-api/
│   ├── Dockerfile
│   └── app/
├── mcp-server/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
└── logs/
```

## 7.3 安全要求

-   MCP 不允许模型直接执行任意写入 Cypher。
-   查询工具使用预定义、参数化的 Cypher。
-   Neo4j 为 MCP 配置独立只读账号。
-   限制查询深度、返回节点数和执行时间。
-   Graph API 使用独立写入账号和 API Token。
-   上传内容限制大小，校验 JSON Schema。
-   日志不得记录密码、Token、数据库连接串等敏感信息。
-   代码证据按需返回，避免批量传输整个源码仓库。

## 7.4 HTTP 传输说明

建议使用当前 MCP SDK 支持的 Streamable HTTP transport。服务地址规划：

``` text
http://ARCH_LAN_IP:8091/mcp
```

正式配置前应核对所使用的 MCP SDK、Zed 和 Codex 版本对 HTTP
transport、认证方式和路径的要求。不要仅凭示例 URL
就认为服务已具备认证和访问控制。

# 8. OpenMetadata 集成

继续复用现有 OpenMetadata 2.0.2，不要为了知识图谱重新部署一套
OpenMetadata。

建议流程：

1.  OpenMetadata Ingestion 采集真实数据库的
    Database、Schema、Table、Column。
2.  Java 分析器解析源码中的实体映射和 SQL 表名。
3.  通过数据库、Schema、表全限定名匹配 OpenMetadata 资产。
4.  Neo4j 保存完整的代码---SQL---表---字段关系。
5.  经过证据校验的正式血缘，再调用 OpenMetadata Lineage API 同步。
6.  同步过程记录批次、成功数、失败数和错误原因。

不要将未经验证的 AI 推断关系直接同步为正式血缘。

# 9. 防火墙与局域网访问

## 9.1 获取 Arch 局域网 IP

``` bash
ip -br addr
ip route
```

为服务器和 Windows 开发机配置稳定的局域网地址或 DHCP 保留地址。

## 9.2 端口开放原则

默认建议：

-   7474：仅管理人员访问；如不需要远程 Browser，不对局域网开放。
-   7687：仅 Graph API/MCP 所在主机访问。
-   8090：仅允许 Windows 分析器 IP 访问。
-   8091：仅允许授权 AI 客户端 IP 访问。
-   5432：不对局域网开放，除非确有管理需求。
-   8585、8080：保持现有 OpenMetadata 规则，不随意改动。

若采用 nftables，规则必须根据当前实际 input chain、Docker
转发规则和已有默认策略整合。不要直接把一条 `accept` 规则粘贴到未知位置。

Windows 测试：

``` powershell
Test-NetConnection ARCH_LAN_IP -Port 8090
Test-NetConnection ARCH_LAN_IP -Port 8091
```

如果使用本机回环绑定的 Neo4j，Windows 不应直接测试 7474/7687；由 Graph
API/MCP 代理访问即可。

# 10. systemd 开机自启

若 Neo4j 使用 Docker Compose，可创建 systemd unit 管理服务生命周期。

`/etc/systemd/system/knowledge-graph.service`：

``` ini
[Unit]
Description=Knowledge Graph Docker Services
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/knowledge-graph
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose stop
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
```

启用：

``` bash
sudo systemctl daemon-reload
sudo systemctl enable --now knowledge-graph.service
sudo systemctl status knowledge-graph.service
```

查看日志：

``` bash
sudo journalctl -u knowledge-graph.service -n 100 --no-pager
sudo docker logs --tail 100 kg-neo4j
```

注意：Compose 中的 `restart: unless-stopped`
负责容器异常退出后的重启；systemd unit 负责主机启动时执行 Compose。若
Compose 文件中尚未加入 Graph API/MCP
服务，启动时只会启动当前定义的服务。

# 11. 备份与恢复

## 11.1 备份原则

-   备份前确认 Neo4j 版本和对应备份方式。
-   对 Neo4j
    使用其支持的在线备份或停机一致性备份流程；不要在数据库运行时简单复制
    `/data` 并假定备份一定一致。
-   PostgreSQL 使用 `pg_dump` 或经验证的物理备份方案。
-   备份 `.env` 和 Compose 配置时，密钥必须安全保存。
-   备份文件放在独立磁盘或其他机器，不要只放在同一块 SSD。
-   定期做恢复演练。

## 11.2 PostgreSQL 逻辑备份示例

``` bash
pg_dump -Fc -d knowledge_graph \
  -f /备份目录/knowledge_graph.dump
```

恢复前先创建目标数据库，再执行：

``` bash
pg_restore -d knowledge_graph \
  /备份目录/knowledge_graph.dump
```

以上命令需根据 PostgreSQL 运行位置、账号认证方式和实际数据库名调整。

# 12. 日常运维检查

``` bash
# 容器状态
docker ps -a

# Docker 空间
docker system df

# 宿主机磁盘
df -h

# 内存
free -h

# 监听端口
sudo ss -lntup

# 服务状态
systemctl status docker
systemctl status knowledge-graph

# Neo4j 日志
docker logs --tail 200 kg-neo4j

# Docker 资源
docker stats --no-stream
```

磁盘空间不足时，先定位具体目录：

``` bash
sudo du -xhd1 /var 2>/dev/null | sort -h
sudo du -xhd1 /srv 2>/dev/null | sort -h
sudo du -xhd1 /opt 2>/dev/null | sort -h
```

不要未经确认就删除 Docker 镜像、容器、卷或 OpenMetadata 数据。

# 13. 部署验收清单

-   [ ] 已确认根分区和数据分区空间。
-   [ ] 已记录现有 OpenMetadata 容器、卷和 Docker 数据目录。
-   [ ] Neo4j 数据目录位于容量足够的持久化分区。
-   [ ] Neo4j 容器启动正常，Browser/Bolt 按预期监听。
-   [ ] Neo4j 唯一约束创建成功。
-   [ ] 图谱测试节点和关系写入、查询成功。
-   [ ] PostgreSQL 数据库或 Schema 已准备。
-   [ ] Graph API 可接收认证后的测试 JSON。
-   [ ] MCP 可通过只读工具查询图谱。
-   [ ] Windows 可访问 8090/8091。
-   [ ] 防火墙只允许必要来源访问。
-   [ ] systemd 开机启动验证通过。
-   [ ] Neo4j 和 PostgreSQL 备份及恢复流程验证通过。
-   [ ] 现有 OpenMetadata 容器和数据未受影响。

# 14. 推荐实施顺序

1.  先检查 Arch 磁盘、内存、Docker RootDir、现有 OpenMetadata 和端口。
2.  确认 Neo4j 数据目录所在分区及资源预算。
3.  部署 Neo4j，验证数据持久化和重启恢复。
4.  确认是否复用现有 PostgreSQL，创建独立数据库或 Schema。
5.  部署 Graph API，完成 Windows JSON 上传闭环。
6.  部署 MCP Server，完成只读查询闭环。
7.  配置防火墙、systemd、备份和恢复。
8.  最后与 OpenMetadata 对齐表标识并同步经过确认的血缘。

------------------------------------------------------------------------

**文档范围说明：** 本手册是 Arch Linux 服务端环境搭建指南。Windows Java
源码分析器、Maven 工程、JavaParser/JSqlParser 代码实现和 Zed/Codex
客户端具体配置，应分别维护在独立文档中。
