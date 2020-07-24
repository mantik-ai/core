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


