#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
重新下载 KAN-MoDTI 权重与依赖文件。

权重（human_final.pth 约 2.5MB）与映射字典、iFeature 数据文件均已随仓库提交，
本脚本用于在文件缺失/损坏时从上游仓库重新拉取。

直接下载 raw.githubusercontent.com 在国内网络下常出现连接重置，
因此这里通过 GitHub API（api.github.com）以 base64 方式下载，更为稳定。

用法：
    python download_weights.py
"""
import base64
import json
import os
import urllib.request

REPO_API = "https://api.github.com/repos/jiahaoxin/KAN-MoDTI"

# 上游路径 -> 本地相对路径
FILES = {
    "human/human_final.pth":              "weights/human_final.pth",
    "human/smiles_mapping.pickle":        "weights/smiles_mapping.pickle",
    "human/wordDict.pickle":              "weights/wordDict.pickle",
    "iFeature/data/Schneider-Wrede.txt":  "data/Schneider-Wrede.txt",
    "iFeature/data/Grantham.txt":         "data/Grantham.txt",
    "model.py":                           "model.py",
    "kan.py":                             "kan.py",
}


def api_get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "SynPharm"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def download(repo_path, local_path):
    meta = api_get(f"{REPO_API}/contents/{repo_path}")
    b64 = meta.get("content")
    if not b64:
        # 文件 > 1MB 时 contents API 不返回内联内容，改走 git blobs API
        meta = api_get(meta["git_url"])
        b64 = meta["content"]
    data = base64.b64decode(b64)
    os.makedirs(os.path.dirname(local_path) or ".", exist_ok=True)
    with open(local_path, "wb") as f:
        f.write(data)
    print(f"OK  {len(data):>8}B  {local_path}")


def main():
    for repo_path, local_path in FILES.items():
        download(repo_path, local_path)
    print("\n全部文件下载完成。")


if __name__ == "__main__":
    main()
