#!/usr/bin/env bash
# ============================================================
# 一键构建 WatchfaceIdTool(小米表盘 ID 工具) Debug + 签名 Release APK
# 用法:bash build.sh  (Termux/ZeroTermux 下请用 bash 执行)
# 前提:JDK 17+、Android SDK、gawk;未设 ANDROID_HOME 时会自动探测
# 显示:终端=单行动态进度条(动画);管道/重定向=逐行中文日志
# 测试:可用 GRADLE_CMD=/path/to/fake-gradlew 替换构建命令
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

GRADLE_CMD="${GRADLE_CMD:-./gradlew}"
TMPD="${TMPDIR:-/data/data/com.termux/files/usr/tmp}"

TICKER_PID=""
AWK_FILE=""
STATE_FILE=""
EVENT_FILE=""
DONE_FILE=""
cleanup() {
  [ -n "$TICKER_PID" ] && kill "$TICKER_PID" 2>/dev/null
  rm -f "$AWK_FILE" "$STATE_FILE" "$EVENT_FILE" "$DONE_FILE" 2>/dev/null
  if [ -t 1 ]; then printf '\033[?25h'; fi
  return 0
}
trap cleanup EXIT
trap 'cleanup; trap - EXIT; exit 130' INT TERM

echo "==> [1/4] 定位 Android SDK"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  for c in "$HOME/android-sdk" "$HOME/Android/Sdk" /opt/android-sdk /usr/local/android-sdk "$HOME/Library/Android/sdk"; do
    if [ -d "$c" ]; then SDK="$c"; break; fi
  done
fi
if [ -z "${SDK:-}" ] || [ ! -d "$SDK" ]; then
  echo "错误: 找不到 Android SDK。" >&2
  echo "请安装 Android SDK 并执行: export ANDROID_HOME=/path/to/android-sdk" >&2
  exit 1
fi
export ANDROID_HOME="$SDK"
echo "      使用 SDK: $SDK"

echo "==> [2/4] 写入 local.properties (sdk.dir=$SDK)"
echo "sdk.dir=$SDK" > local.properties

echo "==> [3/4] 预统计任务总数 (用于进度显示)"
TOTAL=$( { $GRADLE_CMD --console=plain --no-daemon --dry-run assembleDebug assembleRelease 2>/dev/null || true; } \
  | awk '/^> Task / || /^:[^ ]/ {c++} END {print c+0}' )
TOTAL=${TOTAL:-0}
if [ "$TOTAL" -gt 0 ]; then
  echo "      共 $TOTAL 个任务"
else
  echo "      未获取到任务总数,进度将只显示计数"
fi

echo "==> [4/4] 开始构建 (Debug + 签名 Release, keystore 已内置)"

# ---------- 统一的 awk 翻译器 (line/bar 两种模式共用) ----------
AWK_FILE="$(mktemp "$TMPD/gradle-zh.XXXXXX")"
cat > "$AWK_FILE" <<'AWKEOF'
BEGIN {
  D["compile"]="编译";D["merge"]="合并";D["process"]="处理";D["generate"]="生成"
  D["package"]="打包";D["assemble"]="组装";D["check"]="校验";D["create"]="创建"
  D["extract"]="提取";D["parse"]="解析";D["compress"]="压缩";D["optimize"]="优化"
  D["transform"]="转换";D["convert"]="转换";D["desugar"]="脱糖";D["write"]="写入"
  D["prepare"]="准备";D["validate"]="验证";D["verify"]="校验";D["shrink"]="收缩"
  D["expand"]="展开";D["collect"]="收集";D["install"]="安装";D["clean"]="清理"
  D["lint"]="Lint检查";D["minify"]="压缩混淆";D["map"]="映射";D["push"]="推送"
  D["sign"]="签名";D["signing"]="签名";D["strip"]="剥离";D["ingest"]="导入"
  D["dex"]="DEX";D["builder"]="构建器";D["java"]="Java";D["javac"]="（javac）"
  D["pre"]="预";D["post"]="后";D["build"]="构建";D["test"]="测试";D["unit"]="单元"
  D["resources"]="资源";D["resource"]="资源";D["manifest"]="清单";D["manifests"]="清单"
  D["assets"]="资源文件";D["shaders"]="着色器";D["shader"]="着色器"
  D["classes"]="类";D["class"]="类";D["jnilibs"]="JNI库";D["libs"]="库";D["lib"]="库"
  D["splits"]="分包";D["apk"]="APK";D["res"]="资源"
  D["resvalues"]="资源值";D["navigation"]="导航";D["compatible"]="兼容"
  D["screen"]="屏幕";D["deep"]="深层";D["links"]="链接";D["link"]="链接"
  D["metadata"]="元数据";D["kotlin"]="Kotlin";D["with"]="";D["to"]="到";D["for"]="为"
  D["native"]="原生";D["profile"]="配置文件";D["art"]="ART";D["proguard"]="ProGuard"
  D["rules"]="规则";D["file"]="文件";D["files"]="文件"
  D["dependencies"]="依赖";D["dependency"]="依赖";D["synthetics"]="合成类";D["synthetic"]="合成类"
  D["global"]="全局";D["project"]="项目";D["ext"]="扩展";D["config"]="配置";D["configs"]="配置"
  D["versions"]="版本";D["version"]="版本";D["listing"]="列表";D["redirect"]="重定向"
  D["binary"]="二进制";D["shrunk"]="收缩";D["wildcards"]="通配符";D["generated"]="生成的"
  D["configuration"]="配置";D["errors"]="错误";D["error"]="错误";D["duplicateclasses"]="重复类"
  D["source"]="源";D["sources"]="源码";D["set"]="集";D["sets"]="集"
  D["paths"]="路径";D["path"]="路径";D["local"]="本地";D["main"]="主"
  D["debug"]="调试";D["release"]="正式版";D["android"]="Android";D["gradle"]="Gradle"
  D["plugin"]="插件";D["aar"]="AAR";D["jar"]="JAR";D["aarmetadata"]="AAR元数据"
  D["mockable"]="Mock";D["stable"]="稳定";D["stability"]="稳定性";D["duplicate"]="重复"
  D["databinding"]="数据绑定";D["layout"]="布局";D["layouts"]="布局"
  D["startup"]="Startup";D["info"]="信息";D["data"]="数据";D["sdk"]="SDK"
  D["symbols"]="符号";D["symbol"]="符号";D["tables"]="表";D["table"]="表"
  D["folders"]="目录";D["folder"]="目录";D["control"]="控制";D["app"]="App"
  D["tooling"]="工具链";D["javaRes"]="Java资源"
}
function camel(s,   i,nw,words,w,lw,out) {
  s = gensub(/([a-z0-9])([A-Z])/, "\\1 \\2", "g", s)
  s = gensub(/([A-Z])([A-Z][a-z])/, "\\1 \\2", "g", s)
  nw = split(s, words, / +/)
  out = ""
  for (i = 1; i <= nw; i++) {
    w = words[i]; lw = tolower(w)
    if (lw in D) out = out D[lw]
    else out = out w
  }
  return out
}
function emit(l) {
  if (mode == "bar") { print l >> eventfile; close(eventfile) }
  else { print l; fflush() }
}
function setstate(c, u, s) {
  print "count=" c "\ncur=" u "\nstatus=" s > statfile
  close(statfile)
}
function writedone(l) { print l >> donefile; close(donefile) }
# --- 吞掉 Daemon 多行英文警告,压成一句中文 ---
/To honour the JVM settings for this build a single-use Daemon process/ { swallow = 8; next }
swallow > 0 {
  if ($0 ~ /^Daemon will be stopped at the end of the build/) {
    swallow = 0
    emit("提示: Gradle 启动一次性 Daemon 进程（正常现象,耗时数秒）")
  } else { swallow-- }
  next
}
/^> Task / {
  n++
  line = substr($0, 8)
  sub(/[ \t]+$/, "", line)
  split(line, seg, / +/)
  path = seg[1]; st = (2 in seg) ? seg[2] : ""
  sub(/^:/, "", path)
  split(path, pp, /:/)
  tname = pp[length(pp)]
  variant = ""
  if (tname ~ /AndroidTest/) { variant="AndroidTest"; sub(/AndroidTest/, "", tname) }
  else if (tname ~ /Release/) { variant="Release"; sub(/Release/, "", tname) }
  else if (tname ~ /Debug/) { variant="Debug"; sub(/Debug/, "", tname) }
  zh = camel(tname)
  vp = (variant == "" ? "" : "（" variant "）")
  sfx = ""
  if (st == "UP-TO-DATE") sfx = "已是最新"
  else if (st == "NO-SOURCE") sfx = "无源码"
  else if (st == "SKIPPED") sfx = "已跳过"
  else if (st == "FROM-CACHE") sfx = "缓存命中"
  else if (st == "FAILED") sfx = "失败"
  if (mode == "bar") {
    setstate(n, zh vp, sfx)
  } else {
    sfx2 = (sfx == "" ? "" : " · " sfx)
    if (total > 0) printf "[ %2d%%|%d/%d] %s%s%s\n", int(n*100/total), n, total, zh, vp, sfx2
    else printf "[%d] %s%s%s\n", n, zh, vp, sfx2
    fflush()
  }
  next
}
/^> Configure project / { sub(/^> Configure project /, ""); emit("> 配置项目 " $0); next }
/^> / { sub(/^> /, "> "); emit($0); next }
/^WARNING: The option setting .* is experimental\.$/ {
  msg = $0
  sub(/^WARNING: The option setting /, "", msg)
  sub(/ is experimental\.$/, "", msg)
  emit("提示: 实验性选项 " msg "（不影响构建,可忽略）")
  next
}
/^w: / { wk++; next }
/^warning: / { wk++; next }
/^FAILURE: Build failed/ { emit("⚠ 构建失败,错误详情如下（保留英文原文便于排查）:"); emit($0); next }
/^\* What went wrong:/ { sub(/\* What went wrong:/, "* 问题原因:"); emit($0); next }
/^\* Try:/ { sub(/\* Try:/, "* 建议:"); emit($0); next }
/^BUILD SUCCESSFUL/ {
  msg = $0; sub(/^BUILD SUCCESSFUL */, "", msg); sub(/^in /, "", msg); gsub(/[ ]+$/, "", msg)
  if (mode == "bar") { writedone("ok=" msg) }
  else if (msg != "") { printf "✔ 构建成功（耗时 %s）\n", msg; fflush() }
  else { emit("✔ 构建成功") }
  next
}
/^BUILD FAILED/ {
  msg = $0; sub(/^BUILD FAILED */, "", msg); sub(/^in /, "", msg); gsub(/[ ]+$/, "", msg)
  if (mode == "bar") { writedone("fail=" msg) }
  else if (msg != "") { printf "✘ 构建失败（耗时 %s）\n", msg; fflush() }
  else { emit("✘ 构建失败") }
  next
}
match($0, /^([0-9]+) actionable tasks?: (.*)$/, m) {
  rest = m[2]
  gsub(/ executed/, " 已执行", rest)
  gsub(/ up-to-date/, " 已是最新", rest)
  gsub(/ from cache/, " 缓存命中", rest)
  gsub(/ skipped/, " 已跳过", rest)
  gsub(/, /, "，", rest)
  if (mode == "bar") { writedone("stats=" m[1] "|" rest) }
  else { printf "共 %s 个任务: %s\n", m[1], rest; fflush() }
  next
}
{ emit($0) }
END {
  if (mode == "bar") {
    if (wk > 0) writedone("warns=" wk)
    writedone("eof=1")
  }
}
AWKEOF

set +e
if [ -t 1 ]; then
  # ================= 终端模式:单行动态进度条 =================
  STATE_FILE="$(mktemp "$TMPD/buildstate.XXXXXX")"
  EVENT_FILE="$(mktemp "$TMPD/buildevent.XXXXXX")"
  DONE_FILE="$(mktemp "$TMPD/buildbdone.XXXXXX")"
  : > "$STATE_FILE"; : > "$EVENT_FILE"; : > "$DONE_FILE"
  START_TS=$(date +%s)

  ticker() {
    local spin='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    local i=0 k filled fp rp
    local count=0 cur="" status="" offset=0 sz new
    local line
    while :; do
      # 完成检测:eof 标记(DONE_FILE 由 awk 收尾写入)
      if grep -q '^eof=1' "$DONE_FILE" 2>/dev/null; then
        printf '\r\033[K\033[?25h'
        if grep -q '^fail=' "$DONE_FILE" 2>/dev/null; then
          printf '\033[1;31m✘ 构建失败\033[0m \033[2m· 耗时 %ds\033[0m\n' "$(( $(date +%s) - START_TS ))"
        else
          printf '\033[1;32m✔ 构建成功\033[0m \033[2m· 耗时 %ds\033[0m\n' "$(( $(date +%s) - START_TS ))"
        fi
        return 0
      fi
      # 打印事件行(错误/警告等需要展示的行)
      sz=$(wc -c < "$EVENT_FILE" 2>/dev/null); sz=${sz//[!0-9]/}; sz=${sz:-0}
      if [ "$sz" -gt "$offset" ]; then
        new=$(tail -c +"$((offset+1))" "$EVENT_FILE" 2>/dev/null)
        offset=$sz
        if [ -n "$new" ]; then
          printf '\r\033[K\033[?25h%s\n' "$new"
        fi
      fi
      # 读状态
      count=$(sed -n 's/^count=//p' "$STATE_FILE" 2>/dev/null | tail -1); count=${count:-0}
      cur=$(sed -n 's/^cur=//p' "$STATE_FILE" 2>/dev/null | tail -1)
      status=$(sed -n 's/^status=//p' "$STATE_FILE" 2>/dev/null | tail -1)
      [ "$count" -gt "$TOTAL" ] && [ "$TOTAL" -gt 0 ] && count=$TOTAL
      # 状态符号
      local st_g=""
      case "$status" in
        已是最新) st_g="\033[2m✓\033[0m" ;;
        无源码)   st_g="\033[2m–\033[0m" ;;
        已跳过)   st_g="\033[2mø\033[0m" ;;
        缓存命中) st_g="\033[36m⟳\033[0m" ;;
        失败)     st_g="\033[1;31m✗\033[0m" ;;
        "")       st_g="" ;;
        *)        st_g="$status" ;;
      esac
      # 组装进度行
      printf -v line '\r\033[K\033[36m%s\033[0m ' "${spin:i%10:1}"
      i=$((i+1))
      if [ "$TOTAL" -gt 0 ]; then
        pct=$(( count * 100 / TOTAL )); [ "$pct" -gt 100 ] && pct=100
        filled=$(( 20 * count / TOTAL )); [ "$filled" -gt 20 ] && filled=20
        fp=""; rp=""
        for ((k=0; k<filled; k++)); do fp+="━"; done
        for ((k=filled; k<20; k++)); do rp+="╺"; done
        printf -v seg '\033[1m%3d%%\033[0m \033[32m%s\033[0m\033[2m%s\033[0m \033[2m·\033[0m %d/%d' "$pct" "$fp" "$rp" "$count" "$TOTAL"
        line+="$seg"
      else
        line+="$count 个任务"
      fi
      [ -n "$cur" ] && line+=" \033[2m·\033[0m \033[33m$cur\033[0m"
      [ -n "$st_g" ] && line+=" $st_g"
      line+=" \033[2m· $(( $(date +%s) - START_TS ))s\033[0m"
      printf '%b' "$line"
      sleep 0.12
    done
  }
  ticker & TICKER_PID=$!

  $GRADLE_CMD --console=plain --no-daemon assembleDebug assembleRelease 2>&1 \
    | gawk -v mode=bar -v total="$TOTAL" -v statfile="$STATE_FILE" -v eventfile="$EVENT_FILE" -v donefile="$DONE_FILE" -f "$AWK_FILE"
  rc=${PIPESTATUS[0]}

  # 有界等待 ticker 收尾(最多 3s),防止卡死
  for i in $(seq 1 30); do
    kill -0 "$TICKER_PID" 2>/dev/null || break
    sleep 0.1
  done
  if kill -0 "$TICKER_PID" 2>/dev/null; then
    kill -9 "$TICKER_PID" 2>/dev/null
    printf '\r\033[K\033[?25h'
    if [ "$rc" -eq 0 ]; then
      printf '\033[1;32m✔ 构建成功\033[0m \033[2m· 耗时 %ds\033[0m\n' "$(( $(date +%s) - START_TS ))"
    else
      printf '\033[1;31m✘ 构建失败\033[0m \033[2m· 耗时 %ds\033[0m\n' "$(( $(date +%s) - START_TS ))"
    fi
  fi
  wait "$TICKER_PID" 2>/dev/null || true
  TICKER_PID=""
  statssum=$(sed -n 's/^stats=//p' "$DONE_FILE" 2>/dev/null | tail -1) || statssum=""
  warnsum=$(sed -n 's/^warns=//p' "$DONE_FILE" 2>/dev/null | tail -1) || warnsum=""
  cleanup
else
  # ================= 非终端模式:逐行中文日志 =================
  $GRADLE_CMD --console=plain --no-daemon assembleDebug assembleRelease 2>&1 \
    | gawk -v mode=line -v total="$TOTAL" -f "$AWK_FILE"
  rc=${PIPESTATUS[0]}
  statssum=""; warnsum=""
fi
set -e

if [ "$rc" -ne 0 ]; then
  echo "✘ 构建失败 (gradle 退出码 $rc),请查看上方错误详情" >&2
  exit "$rc"
fi
[ -n "$warnsum" ] && echo "提示: 编译器/工具警告 $warnsum 条（已省略原文）"
echo ""
echo "=========================================="
echo "构建成功! APK 位于:"
[ -n "$statssum" ] && printf "  任务统计: 共 %s 个任务: %s\n" "${statssum%%|*}" "${statssum#*|}"
find app/build/outputs/apk -name "*.apk" -type f 2>/dev/null | while read -r f; do
  printf "  %s  %s\n" "$(du -h "$f" | cut -f1)" "$f"
done
echo "=========================================="
