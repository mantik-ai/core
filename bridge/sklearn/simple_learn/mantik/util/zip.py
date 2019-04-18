import os
import zipfile


def zip_directory(directory: str, writer, avoid_hidden=False):
    with zipfile.ZipFile(writer, "w") as zipwriter:
        for root, dirs, files in os.walk(directory):
            basename = os.path.basename(root)
            if avoid_hidden:
                if basename == "__pycache__":
                    continue
                if basename.startswith("."):
                    continue
            for file in files:
                bn = os.path.basename(file)
                if avoid_hidden:
                    if bn.startswith("."):
                        continue
                fp = os.path.join(root, file)
                zp = os.path.relpath(fp, directory)
                print("Zipping {} -> {}".format(fp, zp))
                zipwriter.write(fp, zp)
        zipwriter.close()
