"""pytest 配置：让 tests 能直接 import 顶层模块（util / engine / extract / graph）。"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
