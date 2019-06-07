Mantik Core
===========

Content
-------
.. toctree::
  :maxdepth: 1

  Datatypes and serialization <DataTypes>
  Bridge documentation <Bridges>
  Mantikfile documentation <Mantikfile>
  Executor Documentation <Executor>
  Executor k8s <Executor.Microk8s>
  Samples <Samples>


Code Structure
--------------
- :code:`bridge` Contains Adapters (Bridges) to Data Formats and Algorithms
- :code:`doc` Contains documentation which can be rendered with `sphinx <https://www.sphinx-doc.org/en/master/usage/quickstart.html>`_.
- :code:`ds` Contains Mantik DataTypes and their main serialization format.
- :code:`examples` Contains Examples
- :code:`executor` Contains the Executor, for executing DAG-Execution Plans
- :code:`go_shared` Contains shared Go Code
- :code:`planner` Contains the main interface (Planner)
- :code:`project` Contains Scala Build Information
- :code:`repository` Contains the File and Mantik Artefact Repository
- :code:`testutils` Contains shared Scala Testing Code
