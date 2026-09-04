"""实体消歧 / 稳定 ID 归一化测试。"""

from util import stable_id


def test_whitespace_insensitive():
    assert stable_id("Person", "张伟") == stable_id("Person", " 张伟 ")
    assert stable_id("Person", "张伟") == stable_id("Person", "张 伟")
    # 全角空格 U+3000
    assert stable_id("Person", "张伟") == stable_id("Person", "张\u3000伟")


def test_case_insensitive():
    assert stable_id("Person", "Zhang Wei") == stable_id("Person", "zhang wei")


def test_full_width_to_half_width():
    # 全角 ＡＢＣ → 半角 ABC
    assert stable_id("Person", "ＡＢＣ") == stable_id("Person", "ABC")


def test_different_entities_differ():
    assert stable_id("Person", "张伟") != stable_id("Person", "李雷")


def test_different_types_differ():
    assert stable_id("Person", "晨会") != stable_id("Topic", "晨会")
