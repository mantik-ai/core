# MNP

MNP (Mantik Node Protocol) is a proposal for a next generation communication protocol between Mantik Nodes.

It should solve the following limitations of the current protocol

- Algorithm nodes can not transform data on the fly. They must receive all input data and then transform the response.
- Running bridges can not easily be reconfigured
- Bridges need a init-container for accessing their payload
- Bridges need a side car.
- The coordinator needs to be present with the full execution plan, making the executor more complicated.
- There is no clean description of a single "task Id" within a tree of nodes.

## How MNP works

MNP is meant to be a transport protocol for nodes. It is not mantik specific, and for Mantik their will be 
a not yet designed layer on top of it.

Processes talking MNP have the following properties

- Some session can be startet
- Each session has a set of input and output ports
- Within a session, it is possible to push data into inport ports and read responses from output ports
- During session init, it is possible to wire output ports to other processes input ports. Data generated
  on this output nodes will be automatically forwarded.
  
 
## Implementation details

- The protocol is defined in `mnp/protocol/mnp.proto` and written as gRpc
- There is an implementation of a server and a client in go in `mnp/mnpgo`.
- Mantik specific init data (payload, MantikHeader) must be placed into the `Any` objects 
  and are not yet defined

## Protocol Details

- Nodes are talking MNP on a given URL `mnp://node:networkport`
- Nodes can be requested to start a session, which is referenced by `mnp://node:networkport/sessionId`
  During session init, the requester can define the content type of all input and output ports
  and also forwarding rules
- Using Push/Pull messages with a `taskId` starts independent sub processes within the nodes.
- Input ports can also be referenced by a Url `mnp://node:networkport/sessionId/inputPort`
- The life cycle of all the node and of a session can be managed via special commands
