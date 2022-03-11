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

"""Show the use of CSV Files"""

import logging
import mantik.engine
import mantik.types

logging.basicConfig(level=logging.DEBUG)

with mantik.engine.Client("localhost", 8087) as client:
    with client.enter_session() as session:
        # Test1 CSV with automatic Field detection (all Strings)
        dataset = mantik.engine.load_csv_from_file(client, "../../bridge/csv/examples/01_helloworld/payload")
        fetched = client.fetch_item(dataset)
        print(f"1. Auto CSV:\n{fetched.bundle.render()}")

        # Test2 CSV with fixed type
        dataset = mantik.engine.load_csv_from_file(client, "../../bridge/csv/examples/01_helloworld/payload", data_type=mantik.types.DataType(
            {
                "columns": {
                    "username": "string",
                    "age": "int32"
                }
            }
        ))
        fetched = client.fetch_item(dataset)
        print(f"2. CSV fixed type:\n{fetched.bundle.render()}")

        # Test3 without Skip Header
        dataset = mantik.engine.load_csv_from_file(client, "../../bridge/csv/examples/01_helloworld/payload", skip_header=False)
        fetched = client.fetch_item(dataset)
        print(f"3. Without Skip Header:\n{fetched.bundle.render()}")

        # Test4 CSV with custom Comma
        dataset = mantik.engine.load_csv_from_file(client, "../../bridge/csv/examples/03_customized/payload", skip_header=False, comma="k")
        fetched = client.fetch_item(dataset)
        print(f"4. Custom Comma:\n{fetched.bundle.render()}")


        # Test5 CSV from URL
        dataset = mantik.engine.load_csv_from_url(client, "https://mantik-public-testdata.s3.eu-central-1.amazonaws.com/deniro.csv")
        fetched = client.fetch_item(dataset)
        print(f"5. CSV from url:\n{fetched.bundle.render()}")

        # Test6 CSV from URL with DataType
        dataset = mantik.engine.load_csv_from_url(client, "https://mantik-public-testdata.s3.eu-central-1.amazonaws.com/deniro.csv", data_type=mantik.types.DataType(
            {
                "columns": {
                    "year": "int32",
                    "score": "int32",
                    "title": "string"
                }
            }
        ))
        fetched = client.fetch_item(dataset)
        print(f"6. CSV from url:\n{fetched.bundle.render()}")

