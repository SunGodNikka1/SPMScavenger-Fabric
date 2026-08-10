import json
from pathlib import Path

p = Path(r"d:\Apps\Minecraft Port\Projects\sparkprofile_ai_enriched.json")
data = json.loads(p.read_text(encoding="utf-8"))

print("=== Profile ===")
ps = data["profile_summary"]
print("duration_s", ps["wall_duration_ms"] / 1000)
print("ticks", ps["number_of_ticks"])
print("entities", data["platform_statistics"]["world"]["entity_counts"])
mspt = data["platform_statistics"]["mspt"]["last1m"]
print("mspt median", mspt["median"], "p95", mspt["percentile95"], "max", mspt["max"])

print("\n=== Key sources (self time) ===")
for row in data["source_self_time_summary"][:15]:
    print(
        f"{row['percent_of_all_sampled_thread_time']:.3f}%  "
        f"{row['self_ms_clamped']:.0f}ms  {row['source']}"
    )

print("\n=== spmscavenger top methods by self time ===")
rows = [r for r in data["top_100_methods_by_self_time"] if r.get("source") == "spmscavenger"]
for r in rows[:30]:
    pct = r.get("percent_of_all_sampled_thread_time", 0)
    ms = r.get("self_ms_clamped", 0)
    cls = r.get("class_name", "").split(".")[-1]
    meth = r.get("method_name", "")
    line = r.get("line_number", "")
    print(f"{pct:.4f}%  {ms:5.0f}ms  {cls}.{meth}:{line}")
print("spmscavenger entries in top-100:", len(rows))

# Aggregate inclusive from thread nodes for scavenger classes
thread = data["threads"][0]
nodes = thread["nodes"]
agg = {}
for n in nodes:
    if n.get("source") != "spmscavenger":
        continue
    cls = n.get("class_name", "").split(".")[-1]
    meth = n.get("method_name", "")
    key = f"{cls}.{meth}"
    agg[key] = agg.get(key, 0) + n.get("inclusive_ms_total", 0)

print("\n=== spmscavenger top methods by inclusive (all nodes) ===")
for key, ms in sorted(agg.items(), key=lambda x: -x[1])[:25]:
    print(f"{ms:6.0f}ms  {key}")
