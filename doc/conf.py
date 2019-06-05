import datetime
import pathlib
import sphinx_readable_theme


author = "mantik"
project = "mantik"


#### no need to edit below this line ##

copyright = f"{datetime.datetime.now().year}, {author}"

# version = release = getattr(module, "__version__")

html_theme = "readable"
html_theme_path = [sphinx_readable_theme.get_html_theme_path()]

extensions = ["recommonmark"]

source_suffix = {".rst": "restructuredtext", ".md": "markdown"}

exclude_patterns = ["examples/**/*"]

language = None

html_show_sourcelink = False
html_show_sphinx = False
html_show_copyright = True

default_role = "any"

templates_path = ["_templates"]
html_sidebars = {
    "index": ["sidebar.html"],
    "**": ["sidebar.html", "localtoc.html", "searchbox.html"],
}
