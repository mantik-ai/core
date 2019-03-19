Coordinator - SideCar Protocol
------------------------------

Coordinator and SideCars are communicating with Golang JSON-RPC Calls to each other.

As this calls are one way only, we are creating two connections, one from Coordinator to Sidecar, one back.

All protocol elements can be found in protocol.go.

This package only implements the raw protocols, not the logic on top of it.

0. Fundamentals

- A challenge is used so that we do not connect to the wrong coordinator. There is no security implemented yet.
- The coordinator must know how to be reachable itself and how the clients can be reached.

1. Connecting Phase

- Coordinator sends SideCar a ClaimSideCarRequest
- - If SideCar has no Coordinator yet, it sends the coordinator a HelloCoordinatorRequest
- - If the coordinator detects the same challenge, it answers with HelloCoordinatorResponse
- SideCar responds to ClaimSideCarRequest with ClaimSideCarResponse

2. Communication Phase

- When one or more side cars are attached to the coordinator, you can send requests to them
- Sidears can send status information to the coordinators.

Waiting Phase
-------------
- Sidecars usually wait for their webservices to come alive (return something http status <500)
  (Before that they do not come alive)
- Sidecars wait for their coordinators to connect
- Coordinators are waiting for sidecars
- After all processing, coordinators ask sidecars to stop.

Execution Phase
---------------

- SideCars create TCP streams out of HTTP Resources on their specific nodes in the way the coordinator requests.
- During communication they tell the coordinator their current state
- This way the coordinator can figure out if it's calculation scheme is executed.

Open Points
-----------
- Perhaps it would be good if the coordinator doesn't talk to existing SideCars but it can also spawn them.
- We need another shared secret, for what the SideCars know that the correct Coordinator is talking to them
- It would be great, if the Coordinator can be restarted and the SideCars can be reprogrammed to this new Coordinator.
  (Eventual Consistency)
- RPC Calls do not have their own timeout yet, only the Connect-Calls


