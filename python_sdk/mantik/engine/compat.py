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

# noinspection UnusedImport

from ._stubs.mantik.engine.engine_pb2_grpc import *
from ._stubs.mantik.engine.engine_pb2 import *

from ._stubs.mantik.engine.sessions_pb2 import *
from ._stubs.mantik.engine.sessions_pb2_grpc import *

from ._stubs.mantik.engine.debug_pb2 import *
from ._stubs.mantik.engine.debug_pb2_grpc import *

from ._stubs.mantik.engine.graph_builder_pb2 import *
from ._stubs.mantik.engine.graph_builder_pb2_grpc import *

from ._stubs.mantik.engine.graph_executor_pb2 import *
from ._stubs.mantik.engine.graph_executor_pb2_grpc import *

from ._stubs.mantik.engine.local_registry_pb2 import *
from ._stubs.mantik.engine.local_registry_pb2_grpc import *

from ._stubs.mantik.engine.ds_pb2 import *

from google.protobuf import symbol_database as _symbol_database

_symbols = _symbol_database.Default()._classes
_services = {k: v for k, v in locals().items() if "Stub" in k}
