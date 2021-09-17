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

import abc
import dataclasses

import mantik.types
from . import compat as stubs

import mantik.util


class MantikObject(abc.ABC):
    """Base class for an object in the registry/graph."""

    item: stubs.Any

    def __str__(self):
        return str(self.item)

    @property
    @abc.abstractmethod
    def item_id(self) -> str:
        """Return the ID of the item."""


@dataclasses.dataclass
class MantikArtifact(MantikObject):
    """An artifact from the mantik registry."""

    item: stubs.AddArtifactResponse

    @property
    def item_id(self) -> str:
        return self.item.artifact.item_id

    @property
    def name(self) -> str:
        return self.item_id


@dataclasses.dataclass
class MantikItem(MantikObject):
    """An item in a computational graph."""

    item: stubs.NodeResponse
    bundle: t.Optional[mantik.types.Bundle] = None

    @property
    def item_id(self) -> str:
        return self.item.item_id

    def is_fetched(self) -> bool:
        """Return whether the item's bundle has already been fetched."""
        return self.bundle is not None
