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

from typing import List, Optional
import dataclasses
from google.protobuf.any_pb2 import Any
from enum import IntEnum
from mnp._stubs.mantik.mnp.mnp_pb2 import SS_INITIALIZING, SS_DOWNLOADING, SS_STARTING_UP, SS_READY, SS_FAILED
from mnp._stubs.mantik.mnp.mnp_pb2 import AboutResponse as ProtoAboutResponse, \
    ConfigureInputPort as ProtoConfigureInputPort, ConfigureOutputPort as ProtoConfigureOutputPort, \
    InitRequest as ProtoInitRequest
from mnp.rpc_converter import string_or_none, or_empty_string


# Nice python wrappers for MNP Elements

@dataclasses.dataclass
class AboutResponse:
    name: str
    extra: Optional[Any] = None

    def as_proto(self) -> ProtoAboutResponse:
        return ProtoAboutResponse(
            name=self.name,
            extra=self.extra
        )


@dataclasses.dataclass
class InputPortConfiguration:
    content_type: str

    @classmethod
    def from_proto(cls, p: ProtoConfigureInputPort):
        return cls(content_type=p.content_type)

    def as_proto(self) -> ProtoConfigureInputPort:
        return ProtoConfigureInputPort(content_type=self.content_type)


@dataclasses.dataclass
class OutputPortConfiguration:
    content_type: str
    forwarding: Optional[str] = None

    @classmethod
    def from_proto(cls, p: ProtoConfigureOutputPort):
        return cls(
            p.content_type,
            string_or_none(p.destination_url)
        )

    def as_proto(self) -> ProtoConfigureOutputPort:
        return ProtoConfigureOutputPort(
            content_type=self.content_type,
            destination_url=or_empty_string(self.forwarding)
        )


@dataclasses.dataclass
class PortConfiguration:
    inputs: List[InputPortConfiguration] = dataclasses.field(default_factory=list)
    outputs: List[OutputPortConfiguration] = dataclasses.field(default_factory=list)

    @classmethod
    def from_init(cls, init: ProtoInitRequest):
        return cls(
            list(map(InputPortConfiguration.from_proto, init.inputs)),
            list(map(OutputPortConfiguration.from_proto, init.outputs))
        )

    def inputs_as_proto(self) -> List[ProtoConfigureInputPort]:
        return list(map(InputPortConfiguration.as_proto, self.inputs))

    def outputs_as_proto(self) -> List[ProtoConfigureOutputPort]:
        return list(map(OutputPortConfiguration.as_proto, self.outputs))


class SessionState(IntEnum):
    INITIALIZING = SS_INITIALIZING
    DOWNLOADING = SS_DOWNLOADING
    STARTING_UP = SS_STARTING_UP
    READY = SS_READY
    FAILED = SS_FAILED
