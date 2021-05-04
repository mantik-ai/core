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

from io import BytesIO

class CollectableByteBuffer(BytesIO):
    """
    A Byte buffer which we can collect after it has been closed.
    (Technically it just disables closing)
    """

    def __init__(self) -> None:
        super().__init__(b"")
        self.close_called = False

    def close(self) -> None:
        # Do not close, otherwise we can't read bytes anymore
        self.close_called = True

    def fetch_collected_bytes(self) -> bytes:
        self.seek(0)
        b = self.read()
        super().close()
        return b


