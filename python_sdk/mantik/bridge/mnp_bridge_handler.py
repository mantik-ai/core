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

import logging
import os
import tempfile
import typing as t
import zipfile

import google.protobuf.any_pb2 as protobuf
import requests
import mnp
import mantik.util

import mantik.types
import mantik.bridge._stubs.mantik.bridge.bridge_pb2 as stubs
from . import kinds
from . import mnp_session_handlers

logger = logging.getLogger(__name__)

BridgeProvider = t.Callable[[mantik.types.MantikHeader], kinds.Bridge]


BRIDGE_KIND_SESSION_HANDLERS: t.Dict[str, mnp.SessionHandler] = {
    "dataset": mnp_session_handlers.MnpSessionHandlerForDataSet,
    "algorithm": mnp_session_handlers.MnpSessionHandlerForAlgorithm,
    "trainable": mnp_session_handlers.MnpSessionHandlerForTrainableAlgorithm,
}


class MnpBridgeHandler(mnp.Handler):

    def __init__(
        self, 
        bridge_provider: BridgeProvider, 
        name: str,
        quit_handler: t.Optional[t.Callable[[], None]] = None,
        session_handlers: t.Optional[t.Dict[str, kinds.Bridge]] = None,
    ):
        self.bridge_provider = bridge_provider
        self.name = name
        self.session_handlers = session_handlers or BRIDGE_KIND_SESSION_HANDLERS.copy()
        self.quit_handler = quit_handler

    def about(self) -> mnp.AboutResponse:
        extra = protobuf.Any()
        extra.Pack(stubs.BridgeAboutResponse())
        return mnp.AboutResponse(
            name=self.name,
            extra=extra
        )

    def quit(self):
        if self.quit_handler:
            self.quit_handler()

    def init_session(self, session_id: str, configuration: protobuf.Any, ports: mnp.PortConfiguration,
                     callback: t.Callable[[mnp.SessionState], None] = None) -> mnp.SessionHandler:
        init_config = stubs.MantikInitConfiguration()
        if configuration.Is(stubs.MantikInitConfiguration.DESCRIPTOR):
            configuration.Unpack(init_config)
        else:
            raise ValueError(f"Expected Mantik init configuration, got {configuration}")

        # TODO: Cleanup directory if init fails
        if callback:
            callback(mnp.SessionState.INITIALIZING)

        # TODO: Set SessionState DOWNLOADING when there is a Url present.
        header_file = self._prepare_directory(session_id, init_config)

        if callback:
            callback(mnp.SessionState.STARTING_UP)

        header = mantik.types.MantikHeader.from_file(header_file)
        session_handler = _get_session_handler_for_bridge_kind(header.kind)

        kind = self.bridge_provider(header)
        return session_handler(session_id, header, ports, kind)


    def _prepare_directory(self, session_id: str, init_config: stubs.MantikInitConfiguration) -> str:
        """
        Prepares the temp directory for a bridge.
        Returns Path to the Mantik header in a temp directory, e.g.
        /tmp/fregljergljrgjl/MantikHeader
        """
        tempdir = tempfile.mkdtemp()
        logger.info("Initializing new Mantik Session %s in directory %s", session_id, tempdir)

        header_file = os.path.join(tempdir, "MantikHeader")
        with open(header_file, "w", encoding="utf-8") as f:
            f.write(init_config.header)

        payload = _get_payload(init_config)
        if payload:
            _unpack_payload(payload, init_config.payload_content_type, tempdir)
            os.remove(payload)

        return header_file


def _get_session_handler_for_bridge_kind(kind: str) -> mnp.SessionHandler:
    try:
        return BRIDGE_KIND_SESSION_HANDLERS[kind]
    except KeyError:
        raise mantik.types.UnsupportedBridgeKindError(
            f"{kind} not supported for bridge MantikHeader.kind"
        )

def _get_payload(init_config: stubs.MantikInitConfiguration) -> t.Optional[str]:
    """
    Download the embedded payload into a temporary file, returns None if there is one
    """
    if init_config.url:
        return _get_payload_url(init_config.url)
    elif init_config.content:
        return _get_payload_content(init_config.content)
    else:
        return None


def _get_payload_url(url: str) -> str:
    logger.info("Downloading %s", url)
    file = tempfile.mktemp()
    req = requests.get(url, timeout=60, stream=True)
    # TODO: More timeout checking here
    with open(file, "wb") as f:
        for chunk in req.iter_content(1024 * 1024):
            f.write(chunk)
    return file


def _get_payload_content(content: bytes) -> str:
    file = tempfile.mktemp()
    with open(file, "wb") as f:
        f.write(content)
    return file


def _unpack_payload(payload_file: str, content_type: str, temp_dir: str):
    destination = os.path.join(temp_dir, "payload")
    file_size = os.stat(payload_file).st_size
    if content_type == "application/zip":
        logger.info("Unzipping zip payload in %s of %d bytes to %s", payload_file, file_size, destination)
        zip = zipfile.ZipFile(payload_file)
        zip.extractall(destination)
    else:
        logger.info("Placing pure file of %d bytes into %s", file_size, destination)
        os.rename(payload_file, destination)
