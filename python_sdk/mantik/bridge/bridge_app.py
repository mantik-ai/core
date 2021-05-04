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

import argparse
import io
import os

import mantik.types
from flask import Flask, request, make_response, Response, send_file

import mantik
import mantik.types
from mantik.util import zip_directory
from .algorithm import Algorithm


def create_bridge_app(mantikheader: mantik.types.MantikHeader, algorithm: Algorithm) -> Flask:
    """
    Create the Flask app for a given algorithm.
    """

    app = Flask(__name__)

    @app.route("/")
    def index():
        return "This is a Bridge to {}".format(mantikheader.name)

    @app.route("/type")
    def function_type():
        return Response(mantikheader.type.to_json(), content_type="application/json")

    @app.route("/training_type")
    def training_type():
        return Response(mantikheader.training_type.to_json(), content_type="application/json")

    @app.route("/stat_type")
    def stat_type():
        return Response(mantikheader.stat_type.to_json(), content_type="application/json")

    @app.route("/train", methods=["POST"])
    def train():
        if not mantikheader.has_training:
            return make_response("No training available", 404)
        if algorithm.is_trained:
            return make_response("Algorithm already trained", 429)

        decoded = mantik.types.Bundle.decode(request.content_type, request.stream, mantikheader.training_type)
        algorithm.train(decoded)
        # Stat result is catched by algorihtm wrapper.
        return Response("")

    @app.route("/apply", methods=["POST"])
    def apply():
        if not algorithm.is_trained:
            return make_response("Algorithm not trained", 400)

        decoded = mantik.types.Bundle.decode(request.content_type, request.stream, mantikheader.type.input)
        result = algorithm.apply(decoded)
        result = (result or mantik.types.Bundle()).__add__(mantikheader.type.output)
        encoded = result.encode(request.content_type)
        return Response(encoded, content_type=request.content_type)

    @app.route("/stats")
    def stats():
        if not mantikheader.has_training:
            return make_response("No training available", 404)
        if not algorithm.is_trained:
            return make_response("Algorithm not yet trained", 409)
        result = algorithm.training_stats
        result = (result or mantik.types.Bundle()).__add__(mantikheader.stat_type)
        content_type = request.accept_mimetypes.best_match(mantik.types.MIME_TYPES)
        encoded = result.encode(content_type)
        return Response(encoded, content_type=content_type)

    @app.route("/admin/quit", methods=["POST"])
    def quit_request():
        # Source https://stackoverflow.com/questions/15562446
        func = request.environ.get("werkzeug.server.shutdown")
        if func is None:
            raise RuntimeError("Not running with the Werkzeug Server")
        func()
        return Response("Shutdown requested")

    @app.route("/result")
    def learn_result():
        # Golang is usually doing long-pending calls, but i am not sure if this can be handled by python
        # and if it is good design
        # so maybe we return HTTP 409 and the sidecar tries again in a reasonable time
        if not algorithm.is_trained:
            return make_response("Algorithm not yet trained", 409)

        # Note: we are compressing in Memory. It would be better to compress to a tempary file
        # However send_file doesn't allow to set a hook to delete the file afterwards
        # Ideally would a stream to output approach as Go is using.
        buffer = io.BytesIO()
        zip_directory(algorithm.trained_data_dir, buffer, avoid_hidden=True)
        buffer.seek(0)
        return send_file(buffer, mimetype="application/zip")

    return app


def start_with_args(args, algorithm_provider):
    """ Start the Bridge using parsed arguments.

    :param args: parsed arguments
    :param algorithm_provider: a function which returns the algorithm with given mantikheader.
    """

    mantik_file_path = os.path.join(args.dir, "MantikHeader")
    mantikheader = mantik.types.MantikHeader.load(mantik_file_path)

    print(f"Payload dir\t{mantikheader.payload_dir}")
    print(f"Name\t{mantikheader.name}")
    print(f"Debug\t{args.d}")
    print(f"Port\t{args.port}")
    print(f"Interface\t{args.interface}")

    algorithm = algorithm_provider(mantikheader)

    app = create_bridge_app(mantikheader, algorithm)
    app.run(debug=args.d, host=args.interface, port=args.port)


def start(algorithm_provider):
    """
    Start the bridge using an algorithm provider
    :param algorithm_provider: a function which returns the algorithm with given mantikheader.
    :return:
    """

    ap = argparse.ArgumentParser()
    ap.add_argument("dir", help="Directory where MantikHeader is present")
    ap.add_argument("-d", required=False, action="store_true", help="Enable Debug Mode")
    ap.add_argument("--port", type=int, default=8502)
    ap.add_argument("--interface", default="0.0.0.0", help="Listening interface")
    start_with_args(ap.parse_args(), algorithm_provider)
