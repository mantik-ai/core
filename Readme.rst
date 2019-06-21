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
  Debugging with Minikube <Minikube>

Code Structure
--------------
- :code:`bridge` Contains Adapters (Bridges) to Data Formats and Algorithms
- :code:`doc` Contains documentation which can be rendered with `sphinx <https://www.sphinx-doc.org/en/master/usage/quickstart.html>`_.
- :code:`ds` Contains Mantik DataTypes and their main serialization format.
- :code:`elements` Contains the basic Mantik definitions: Mantikfile, various Definitions.
- :code:`examples` Contains Examples
- :code:`executor` Contains the Executor, for executing DAG-Execution Plans
- :code:`go_shared` Contains shared Go Code
- :code:`planner` Contains the local application and interface for planning and executing jobs.
- :code:`project` Contains Scala Build Information
- :code:`testutils` Contains shared Scala Testing Code
