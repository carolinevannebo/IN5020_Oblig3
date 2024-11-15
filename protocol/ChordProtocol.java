package protocol;

import crypto.ConsistentHashing;
import fingertable.FingerTable;
import fingertable.FingerTableEntry;
import fingertable.Interval;
import p2p.NetworkInterface;
import p2p.NodeInterface;

import java.util.*;

/**
 * This class implements the chord protocol. The protocol is tested using the custom-built simulator.
 */
public class ChordProtocol implements Protocol {
    // length of the identifier that is used for consistent hashing
    public int m;

    // network object
    public NetworkInterface network;

    // consistent hashing object
    public ConsistentHashing ch;

    // key indexes. tuples of (<key name>, <key index>)
    public HashMap<String, Integer> keyIndexes;

    public ChordProtocol(int m) {
        this.m = m;
        setHashFunction();
        this.keyIndexes = new HashMap<String, Integer>();
    }

    /**
     * sets the hash function
     */
    public void setHashFunction(){
        this.ch = new ConsistentHashing(this.m);
    }

    /**
     * sets the network
     * @param network the network object
     */
    public void setNetwork(NetworkInterface network){
        this.network = network;
    }

    /**
     * sets the key indexes. Those key indexes can be used to  test the lookup operation.
     * @param keyIndexes - indexes of keys
     */
    public void setKeys(HashMap<String, Integer> keyIndexes){
        this.keyIndexes = keyIndexes;
    }

    /**
     *
     * @return the network object
     */
    public NetworkInterface getNetwork(){
        return this.network;
    }

    /**
     * This method builds the overlay network.  It assumes the network object has already been set. It generates indexes
     *     for all the nodes in the network. Based on the indexes it constructs the ring and places nodes on the ring.
     *         algorithm:
     *           1) for each node:
     *           2)     find neighbor based on consistent hash (neighbor should be next to the current node in the ring)
     *           3)     add neighbor to the peer (uses Peer.addNeighbor() method)

     * 1. initialize: for each node in topology, generate a hash-value using consistent hashing and set the node index
     * 2. sort: sort nodes by id to ensure ring topology
     * 3. update topology: clear LinkedHashMap and repopulate it with sorted nodes to maintain order
     * 4. add neighbor: to maintain ring, loop through sorted nodes,
     *    for each node find the next
     *    using modulo to ensure that the ring "wraps around",
     *    ie that the last node connects back to the first node
     */
    public void buildOverlayNetwork() {
        LinkedHashMap<String, NodeInterface> topology = this.network.getTopology();

        for (Map.Entry<String, NodeInterface> entry : topology.entrySet()) {
            String nodeName = entry.getKey();
            int nodeIndex = ch.hash(nodeName); // consistent hashing
            NodeInterface node = entry.getValue();
            node.setId(nodeIndex);
        }

        // sort nodes by id to ensure ring topology
        List<Map.Entry<String, NodeInterface>> sortedNodes = new ArrayList<>(topology.entrySet());
        sortedNodes.sort(Comparator.comparingInt(entry -> entry.getValue().getId()));

        // clear and replace topology with sorted nodes
        topology.clear();
        for (Map.Entry<String, NodeInterface> entry : sortedNodes) {
            topology.put(entry.getKey(), entry.getValue());
        }

        // add neighbour to peer node
        int nodeCount = sortedNodes.size();
        for (int i = 0; i < nodeCount; i++) {
            NodeInterface currentNode = sortedNodes.get(i).getValue();
            NodeInterface nextNode = sortedNodes.get((i + 1) % nodeCount).getValue(); // ensure ring topology by wrapping around
            currentNode.addNeighbor(nextNode.getName(), nextNode);
        }
    }

    /**
     * This method builds the finger table. The finger table is the routing table used in the chord protocol to perform
     * lookup operations. The finger table stores m-entries. Each ith entry points to the ith finger of the node.
     * Each ith entry stores the information of it's neighbor that is responsible for indexes ((n+2^i-1) mod 2^m).
     * i = 1,...,m.

     * Each finger table entry should consists of
     *     1) start value - (n+2^i-1) mod 2^m. i = 1,...,m
     *     2) interval - [finger[i].start, finger[i+1].start]
     *     3) node - first node in the ring that is responsible for indexes in the interval
     */
    public void buildFingerTable() {
        System.out.println("\tBuilding the finger tables...");
        List<NodeInterface> nodes = new ArrayList<>(this.network.getTopology().values());

        // build finger table
        for (NodeInterface node : nodes) {
            int nodeId = node.getId();
            FingerTable fingerTable = new FingerTable(m);

            for (int i = 1; i <= m; i++) {
                // calculate interval: (start, end)
                // handle wrap-around case for the last entry
                int start = (nodeId + (1 << (i - 1))) % (1 << m); // calculate starting value for entry
                int end = (i == m) ? nodeId : ((nodeId-1) + (1 << i)) % (1 << m);

                Interval interval = new Interval(start, end);
                NodeInterface successor = findSuccessor(start, node.getSuccessor(), nodeId, m); // find successor node for starting value

                if (successor != null) {
                    FingerTableEntry entry = new FingerTableEntry(start, interval, successor);
                    fingerTable.addEntry(entry); // add entry to finger table
                    //System.out.println("adding entry: " + entry.toString() + " for successor " + successor.getId());
                }
            }
            // set finger table for current node
            node.setRoutingTable(fingerTable);
        }
    }

    private NodeInterface findSuccessor(int start, NodeInterface candidate, int nodeId, int m) {
        int newStart = start <= nodeId ? (int) (start + Math.pow(2, m)) : start;
        int candidateId = candidate.getId() <= nodeId ? (int) (candidate.getId() + Math.pow(2, m)) : candidate.getId();

        while (candidateId < newStart) {
            candidate = candidate.getSuccessor();
            candidateId = candidate.getId() <= nodeId ? (int) (candidate.getId() + Math.pow(2, m)) : candidate.getId();
        }
        return candidate;
    }

    /**
     * This method performs the lookup operation.
     *  Given the key index, it starts with one of the node in the network and follows through the finger table.
     *  The correct successors would be identified and the request would be checked in their finger tables successively.
     *  Finally, the request will reach the node that contains the data item.

     *  1. start from a single chosen node for all lookups.
     *  2. check if the current node holds the key.
     *  3. if not, select the next node by consulting the finger table.
     *  4. continue until the responsible node is found,
     *      keeping track of each node visited (for hop count and route).

     * @param keyIndex index of the key
     * @return names of nodes that have been searched and the final node that contains the key
     */
    public LookUpResponse lookUp(int keyIndex) {
        NodeInterface currentNode = network.getTopology().get("Node 1");
        LinkedHashSet<String> route = new LinkedHashSet<>();
        System.out.println("Looking up EntrySet value " + keyIndex);

        while (true) {
            // check if current node or its successor contains the key
            LookUpResponse response = getResponseForNode(route, keyIndex, currentNode);
            if (response != null) return response;

            // traverse finger table to find next appropriate node
            FingerTable fingerTable = (FingerTable) currentNode.getRoutingTable();
            NodeInterface nextNode = findNextNode(fingerTable, keyIndex);

            // check if lookup wraps around to start of ring
            if (nextNode == null || nextNode.equals(currentNode)) break;

            route.add(currentNode.getName());
            currentNode = nextNode;
        }
        return getResponseForNode(route, keyIndex, currentNode);
    }

    private LookUpResponse getResponseForNode(LinkedHashSet<String> route, int keyIndex, NodeInterface node) {
        LookUpResponse response = new LookUpResponse(route, keyIndex, node.getName());
        LinkedHashSet<Integer> dataItems = (LinkedHashSet<Integer>) node.getData();
        for (Integer data : dataItems) {
            if (data == keyIndex) {
                //System.out.println(response.toString());
                return response;
            }
        }
        return null;
    }

    private NodeInterface findNextNode(FingerTable fingerTable, int keyIndex) {
        for (int i = 0; i < fingerTable.getEntries().size(); i++) {
            FingerTableEntry entry = fingerTable.getEntries().get(i);

            if (entry.interval().contains(keyIndex)) {
                return entry.successor();
            }
        }
        return null;
    }
}
