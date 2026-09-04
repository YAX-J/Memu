"""嵌入模块测试：n-gram 向量性质 + 余弦相似度。"""

import numpy as np
import pytest

import embedding
from embedding import _hashing_vector, cosine


@pytest.fixture(autouse=True)
def isolated_cache(tmp_path, monkeypatch):
    """把磁盘缓存指到临时文件，避免污染真实 data 目录。"""
    monkeypatch.setattr(embedding, "_CACHE_PATH", tmp_path / "emb.json")
    monkeypatch.setattr(embedding, "_cache", {})
    yield


def test_hashing_vector_deterministic():
    a = _hashing_vector("张伟参加晨会")
    b = _hashing_vector("张伟参加晨会")
    assert np.array_equal(a, b)


def test_hashing_vector_normalized():
    v = _hashing_vector("张伟参加晨会")
    assert abs(float(np.linalg.norm(v)) - 1.0) < 1e-4


def test_empty_text_zero_vector():
    assert float(np.linalg.norm(_hashing_vector(""))) == 0.0


def test_similar_texts_score_higher():
    a = _hashing_vector("张伟参加晨会")
    near = _hashing_vector("张伟参加了晨会")
    far = _hashing_vector("今天完成发版上线")
    assert cosine(a, near) > cosine(a, far)


def test_cosine_identical_is_one():
    v = _hashing_vector("韩梅梅周末去健身")
    assert abs(cosine(v, v) - 1.0) < 1e-4


def test_embed_returns_normalized_and_cached():
    v = embedding.embed("李雷参加周会")
    assert isinstance(v, np.ndarray)
    assert abs(float(np.linalg.norm(v)) - 1.0) < 1e-4
    # 再次调用命中缓存，结果一致
    v2 = embedding.embed("李雷参加周会")
    assert np.array_equal(v, v2)
