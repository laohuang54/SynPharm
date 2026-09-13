"""
从 HuggingFace 下载 FlashPPI 权重到本地 ./weights 目录。

权重（model.safetensors 约 2.7GB）不随 git 提交（GitHub 单文件 100MB 上限），
克隆仓库后运行本脚本即可重新拉取。

用法:
    python download_weights.py

依赖:
    pip install huggingface_hub
"""

from pathlib import Path

from huggingface_hub import snapshot_download

REPO_ID = "tattabio/flashppi"
LOCAL_DIR = Path(__file__).resolve().parent / "weights"

# 忽略 HF 仓库里的两个 OpenMP 残留文件（无实际用途）
IGNORE = ["__KMP_REGISTERED_LIB_*"]

if __name__ == "__main__":
    print(f"正在下载 {REPO_ID} -> {LOCAL_DIR} ...")
    snapshot_download(repo_id=REPO_ID, local_dir=str(LOCAL_DIR), ignore_patterns=IGNORE)
    print("下载完成。")
