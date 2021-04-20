Mantik Core
===========

Mantik Core is a runtime engine designed for evaluating, executing and deploying AI/ML Applications.
It is designed around Mantik Items which can be combined into a graph.

Various Frameworks and Data-Formats are encapsulated through Bridges.

**Note:** Mantik Definitions are not stable and subject of changes.

Content
-------
.. toctree::
  :maxdepth: 1

  Getting Started <GettingStarted>
  Building <Building>
  Datatypes and serialization <DataTypes>
  MNP Protocol <Mnp>
  Bridge documentation <Bridges>
  MantikHeader documentation <MantikHeader>
  Executor Documentation <Executor>
  Samples <Samples>
  Debugging with Minikube <Minikube>
  Mantik ID <MantikId>
  Glossary <Glossary>
  Architecture <Architecture>
  SQL Support <Sql>

Code Structure
--------------
- :code:`bridge` Contains Adapters (Bridges) to Data Formats and Algorithms
- :code:`cli` Mantik Command line Client
- :code:`doc` Contains documentation which can be rendered with `sphinx <https://www.sphinx-doc.org/en/master/usage/quickstart.html>`_.
- :code:`componently` Scala Helper library, simplifying use of Akka, gRpc and Component-Building.
- :code:`ds` Contains Mantik DataTypes and their main serialization format.
- :code:`elements` Contains the basic Mantik definitions: MantikHeader, various Definitions.
- :code:`examples` Contains Examples
- :code:`executor` Contains the Executor, for executing DAG-Execution Plans
- :code:`go_shared` Contains shared Go Code
- :code:`planner` Contains the local application and interface for planning and executing jobs.
- :code:`project` Contains Scala Build Information
- :code:`testutils` Contains shared Scala Testing Code

Contributing
------------

Contributions are welcome!

If you want to contribute code, please sign a contributor agreement.

For more information see `https://mantik.ai/contributing <https://mantik.ai/contributing>`_.

Copyright
---------

Copyright 2020-2021 Mantik UG (Haftungsbeschränkt)
All rights reserved.

Mantik is licensed unter the Terms of

GNU Affero General Public License v3.

See LICENSE.md

Linking Exception
*****************

Additionally, the following linking exception is granted:

.. code-block::

   Additional permission under the GNU Affero GPL version 3 section 7:

   If you modify this Program, or any covered work, by linking or
   combining it with other code, such other code is not for that reason
   alone subject to any of the requirements of the GNU Affero GPL
   version 3.


Proprietary Licenses
********************

Mantik UG (Haftungsbeschränkt) reserves the right to sell proprietary licenses.