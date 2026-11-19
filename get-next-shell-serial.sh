#!/usr/bin/env bash
# 查询 Nexus 中指定构件、指定版本前缀下的最大版本号，并把对应 jar 下载到本地。
#
# 用法：
#   bash get-next-shell-serial.sh [--detail] <版本前缀> <artifactId> <groupId> [下载目录]
#
# 示例：
#   bash get-next-shell-serial.sh 4.2.1.shell zfs-fms-config-core cn.ztessc
#   bash get-next-shell-serial.sh 4.1.2 zfs-op-server cn.ztessc /tmp/download
#
# 下载目录不传时默认当前目录，也可用环境变量 DOWNLOAD_DIR 指定。
#
# 默认输出（版本号、下载链接、本地文件路径）：
#   4.1.2.6.7
#   http://gitlab.ztccloud.com.cn:8088/nexus/content/repositories/releases/cn/ztessc/zfs-op-server/4.1.2.6.7/zfs-op-server-4.1.2.6.7.jar
#   SAVED_FILE=./zfs-op-server-4.1.2.6.7.jar
#
# 使用 --detail 时列出所有匹配版本及最大版本：
#   MAX_VERSION=4.2.1.shell.0819.10
#   DOWNLOAD_URL=http://.../zfs-config-core-4.2.1.shell.0819.10.jar
#   SAVED_FILE=./zfs-config-core-4.2.1.shell.0819.10.jar

set -euo pipefail

DETAIL=false
if [[ "${1:-}" == "--detail" ]]; then
    DETAIL=true
    shift
fi

if [[ $# -lt 3 || $# -gt 4 ]]; then
    echo "用法：bash $0 [--detail] <版本前缀> <artifactId> <groupId> [下载目录]" >&2
    echo "示例：bash $0 4.2.1.shell zfs-fms-config-core cn.ztessc" >&2
    exit 2
fi

VERSION_PREFIX="$1"
ARTIFACT_ID="$2"
GROUP_ID="$3"
DOWNLOAD_DIR="${4:-${DOWNLOAD_DIR:-.}}"

NEXUS_INDEX_URL="${NEXUS_INDEX_URL:-http://gitlab.ztccloud.com.cn:8088/nexus/service/local/repositories/releases/index_content/}"
NEXUS_USER="${NEXUS_USER:-songqi}"
NEXUS_PASSWORD="${NEXUS_PASSWORD:-songqi@202309}"

if [[ ! "$VERSION_PREFIX" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
    echo "错误：版本前缀格式不合法：$VERSION_PREFIX" >&2
    exit 2
fi

tmp_response=$(mktemp)
trap 'rm -f "$tmp_response"' EXIT

query_url="${NEXUS_INDEX_URL}?groupIdHint=${GROUP_ID}&artifactIdHint=${ARTIFACT_ID}"

# NEXUS_RESPONSE_FILE 仅用于离线验证；正常运行时直接请求 Nexus。
if [[ -n "${NEXUS_RESPONSE_FILE:-}" ]]; then
    cp "$NEXUS_RESPONSE_FILE" "$tmp_response"
else
    if ! curl --fail --silent --show-error --connect-timeout 10 \
        --max-time 60 \
        --user "${NEXUS_USER}:${NEXUS_PASSWORD}" \
        "$query_url" --output "$tmp_response"; then
        echo "错误：Nexus 查询失败：$query_url" >&2
        exit 1
    fi
fi

# Nexus 可能返回 JSON 或 XML，两种格式的 nodeName 都提取。
# 过滤规则：
#   1. 必须以指定前缀开头（文件节点如 zfs-xxx-4.2.1.shell.0819.1.jar 不带前缀开头，自然被排除）
#   2. sort -V 做版本号感知排序，保证 4.2.1.shell.0819.10 > 4.2.1.shell.0819.9
matched_versions=$(
    sed -n \
        -e 's/.*"nodeName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
        -e 's|.*<nodeName>[[:space:]]*\([^<]*\)[[:space:]]*</nodeName>.*|\1|p' \
        "$tmp_response" |
        grep -E "^${VERSION_PREFIX}" |
        sort -u -V
)

if [[ -z "$matched_versions" ]]; then
    echo "错误：Nexus 中不存在前缀为 ${VERSION_PREFIX} 的版本（${GROUP_ID}:${ARTIFACT_ID}）" >&2
    exit 1
fi

max_version=$(echo "$matched_versions" | tail -n 1)

# 下载链接按 <path>/cn/ztessc/<artifactId>/<version>/<artifactId>-<version>.jar</path> 生成：
# 优先从响应中提取该 jar 的 path 节点，取不到时按 groupId/artifactId/版本规则拼接兜底。
group_path=${GROUP_ID//./\/}
jar_path="/${group_path}/${ARTIFACT_ID}/${max_version}/${ARTIFACT_ID}-${max_version}.jar"

download_path=$(
    sed -n \
        -e 's/.*"path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
        -e 's|.*<path>[[:space:]]*\([^<]*\)[[:space:]]*</path>.*|\1|p' \
        "$tmp_response" |
        grep -F "${jar_path}" |
        head -n 1
)

if [[ -z "$download_path" ]]; then
    download_path="$jar_path"
fi

nexus_base="${NEXUS_INDEX_URL%%/service/*}"
download_url="${nexus_base}/content/repositories/releases${download_path}"

# 下载 jar 到本地
if [[ ! -d "$DOWNLOAD_DIR" ]]; then
    mkdir -p "$DOWNLOAD_DIR"
fi

file_name=$(basename "$download_path")
target_file="${DOWNLOAD_DIR%/}/$file_name"

if [[ -f "$target_file" && "${SKIP_EXISTS:-false}" == true ]]; then
    echo "文件已存在，跳过下载：$target_file" >&2
else
    if ! curl --fail --show-error --progress-bar --connect-timeout 10 \
        --max-time 600 \
        --user "${NEXUS_USER}:${NEXUS_PASSWORD}" \
        -o "$target_file" "$download_url"; then
        rm -f "$target_file"
        echo "错误：下载失败：$download_url" >&2
        exit 1
    fi
fi

if [[ "$DETAIL" == true ]]; then
    echo "GROUP_ID=${GROUP_ID}"
    echo "ARTIFACT_ID=${ARTIFACT_ID}"
    echo "VERSION_PREFIX=${VERSION_PREFIX}"
    echo "matched versions:"
    echo "$matched_versions" | sed 's/^/  /'
    echo "MAX_VERSION=${max_version}"
    echo "DOWNLOAD_URL=${download_url}"
    echo "SAVED_FILE=${target_file}"
else
    echo "$max_version"
    echo "$download_url"
    echo "SAVED_FILE=${target_file}"
fi
