from typing import Optional

def string_or_none(s: str) -> Optional[str]:
    return s or None


def or_empty_string(s: Optional[str]) -> str:
    return s or ''
