// Render the RunGraph in mermaidjs notation

function renderRunGraph(graph) {
    const lines = []
    lines.push("flowchart LR")
    renderNodes(lines, graph.nodes)
    renderLinks(lines, graph.links)
    return lines.join("\n")
}

function renderNodes(lines, nodes) {
    Object.entries(nodes).forEach ((keyValue) => {
        lines.push(renderNode(keyValue[0], keyValue[1]))
    });
}

function renderNode(id, node) {
    return `${id}[${renderNodeContent(node)}]\nstyle ${id} ${renderNodeStyle(node)}`
}

function renderNodeContent(node) {
    switch (node.type) {
        case "file":
            return "File";
        case "mnp":
            return `Mnp<br>${node.image}`;
    }
}

function renderNodeStyle(node) {
    switch (node.type) {
        case "file":
            return "fill:#91ffeb";
        case "mnp":
            return "fill:#fff1b0";
    }
}

function renderLinks(lines, links) {
    links.forEach(link => {
        lines.push(renderLink(link));
    })
}

function renderLink(link) {
    return `${link.from} --> ${link.to}`
}

export default renderRunGraph;