package org.kiwiproject.registry.eureka.config;

import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.joining;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Custom Jackson {@link JsonDeserializer} to examine a node and take different action based on whether it is
 * a {@link TextNode} or a container node. If it is a TextNode, return the node's text, otherwise collect the
 * values in the container and join them into a CSV string.
 * <p>
 * This implementation does not perform extensive error checking; it assumes the element being deserialized
 * contains either a TextNode or is a container of TextNode. For example, as a TextNode in YAML:
 *
 * <pre>
 * someProperty: value1,value2,value3
 * </pre>
 * <p>
 * And as a container node:
 *
 * <pre>
 * someProperty:
 *   - value1
 *   - value2
 *   - value3
 * </pre>
 */
class ListToCsvStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var treeNode = p.getCodec().readTree(p);

        if (treeNode.isContainerNode()) {
            return containerNodeToCsvString(treeNode);
        }

        return verifyIsTextNode(treeNode).asText();
    }

    private static String containerNodeToCsvString(TreeNode treeNode) {
        return IntStream.range(0, treeNode.size())
                .mapToObj(index -> textNodeToString(treeNode, index))
                .collect(joining(","));
    }

    private static String textNodeToString(TreeNode treeNode, int index) {
        var node = treeNode.get(index);
        return verifyIsTextNode(node).asText();
    }

    private static TextNode verifyIsTextNode(TreeNode node) {
        verify(node instanceof TextNode, "expected node to be TextNode but was: %s", node.getClass().getName());
        //noinspection ConstantConditions
        return (TextNode) node;
    }
}
