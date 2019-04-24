import argparse
import os
from flask import Flask, request, make_response, Response, send_file
import mantik
from algorithm_wrapper import Algorithm
from mantik.util.zip import zip_directory
import io


def create_app(mantikfile: mantik.Mantikfile, algorithm: Algorithm):
    app = Flask(__name__)

    @app.route("/")
    def index():
        return "This is a Bridge to {}".format(mantikfile.name)

    @app.route("/type")
    def function_type():
        return Response(mantikfile.type.to_json(), content_type="application/json")

    @app.route("/training_type")
    def training_type():
        return Response(mantikfile.training_type.to_json(), content_type="application/json")

    @app.route("/stat_type")
    def stat_type():
        return Response(mantikfile.stat_type.to_json(), content_type="application/json")

    @app.route("/train", methods=['POST'])
    def train():
        if not mantikfile.has_training():
            return make_response("No training available", 404)

        decoded = mantik.Bundle.decode(request.content_type, request.stream, mantikfile.training_type)
        algorithm.train(decoded)
        # Stat result is catched by algorihtm wrapper.
        return Response("")

    @app.route("/apply", methods=['POST'])
    def apply():
        if not algorithm.is_trained:
            return make_response("Algorithm not trained", 400)

        decoded = mantik.Bundle.decode(request.content_type, request.stream, mantikfile.type.input)
        result = algorithm.apply(decoded)
        result = result if result is not None else mantik.Bundle()
        result = result.with_type_if_missing(mantikfile.type.output)
        encoded = result.encode(request.content_type)
        return Response(encoded, content_type=request.content_type)

    @app.route("/stats")
    def stats():
        if not mantikfile.has_training():
            return make_response("No training available", 404)
        if not algorithm.is_trained:
            return make_response("Algorithm not yet trained", 409)
        result = algorithm.training_stats
        result = result if result is not None else mantik.Bundle()
        result = result.with_type_if_missing(mantikfile.stat_type)
        content_type = request.accept_mimetypes.best_match(mantik.MIME_TYPES)
        encoded = result.encode(content_type)
        return Response(encoded, content_type=content_type)

    @app.route("/admin/quit", methods=["POST"])
    def quit_request():
        # Source https://stackoverflow.com/questions/15562446
        func = request.environ.get('werkzeug.server.shutdown')
        if func is None:
            raise RuntimeError('Not running with the Werkzeug Server')
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
        zip_directory(algorithm.directory, buffer, avoid_hidden=True)
        buffer.seek(0)
        return send_file(buffer, mimetype="application/zip")

    return app


def start(args):
    mantik_file_path = os.path.join(args.dir, "Mantikfile")
    mantikfile = mantik.Mantikfile.load(mantik_file_path)

    print("Directory    ", mantikfile.directory)
    print("Payload dir  ", mantikfile.payload_dir())
    print("Name         ", mantikfile.name)
    print("Debug        ", args.d)
    print("Port         ", args.port)
    print("Interface    ", args.interface)

    algorithm = Algorithm(mantikfile.payload_dir())
    algorithm.try_init_catching()

    app = create_app(mantikfile, algorithm)
    app.run(debug=args.d, host=args.interface, port=args.port)


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument("dir", help="Directory where Mantikfile is present")
    ap.add_argument("-d", required=False, action='store_true', help="Enable Debug Mode")
    ap.add_argument("--port", type=int, default=8502)
    ap.add_argument("--interface", default="0.0.0.0", help="Listening interface")
    start(ap.parse_args())