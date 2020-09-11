from mnp import MnpUrl


def test_parsing():
    base = "mnp://1.2.3.4:32000/sessionFoo/1234"
    url = MnpUrl.parse(base)
    assert url.address == "1.2.3.4:32000"
    assert url.session_id == "sessionFoo"
    assert url.port == 1234
    assert url.format() == base
