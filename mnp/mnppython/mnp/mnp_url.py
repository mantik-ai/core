from __future__ import annotations
from urllib.parse import urlparse

class MnpUrl:

    def __init__(self, address: str, session_id: str, port: int):
        self.address = address
        self.session_id = session_id
        self.port = port

    @classmethod
    def parse(cls, s: str) -> MnpUrl:
        try:
            parsed = urlparse(s)
            if parsed.scheme != "mnp":
                raise ValueError("Expected scheme mnp")
            address = "{}:{}".format(parsed.hostname, parsed.port)
            elements = list(filter(None, parsed.path.split("/")))
            session_id = elements[0]
            port = int(elements[1])
            return cls(address, session_id, port)
        except Exception as e:
            raise ValueError("Invalid url", e)

    def format(self) -> str:
        return "mnp://{}/{}/{}".format(self.address, self.session_id, self.port)
