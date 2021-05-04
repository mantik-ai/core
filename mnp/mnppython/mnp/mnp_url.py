#
# This file is part of the Mantik Project.
# Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
# Authors: See AUTHORS file
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License version 3.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.
#
# Additionally, the following linking exception is granted:
#
# If you modify this Program, or any covered work, by linking or
# combining it with other code, such other code is not for that reason
# alone subject to any of the requirements of the GNU Affero GPL
# version 3.
#
# You can be released from the requirements of the license by purchasing
# a commercial license.
#

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
