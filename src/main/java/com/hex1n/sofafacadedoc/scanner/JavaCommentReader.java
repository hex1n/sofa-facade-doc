package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class JavaCommentReader {
    private final JavaAnnotationReader annotations;

    public JavaCommentReader(JavaAnnotationReader annotations) {
        this.annotations = annotations;
    }

    public String javadoc(NodeWithAnnotations<?> node) {
        if (node instanceof NodeWithJavadoc) {
            return ((NodeWithJavadoc<?>) node).getJavadoc().map(j -> j.toText()).orElse("");
        }
        return "";
    }

    public String methodMain(MethodDeclaration md) {
        return md.getJavadoc().map(j -> j.getDescription().toText().trim()).orElse("");
    }

    public Map<String, String> paramComments(MethodDeclaration md) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!md.getJavadoc().isPresent()) return out;
        for (JavadocBlockTag tag : md.getJavadoc().get().getBlockTags()) {
            if (tag.getType() == JavadocBlockTag.Type.PARAM && tag.getName().isPresent()) {
                out.put(tag.getName().get(), tag.getContent().toText());
            }
        }
        return out;
    }

    public String returnComment(MethodDeclaration md) {
        if (!md.getJavadoc().isPresent()) return "";
        for (JavadocBlockTag tag : md.getJavadoc().get().getBlockTags()) {
            if (tag.getType() == JavadocBlockTag.Type.RETURN) return tag.getContent().toText();
        }
        return "";
    }

    public String comment(NodeWithAnnotations<?> node) {
        if (node instanceof NodeWithJavadoc && ((NodeWithJavadoc<?>) node).getJavadoc().isPresent()) {
            return ((NodeWithJavadoc<?>) node).getJavadoc().get().toText();
        }
        if (node instanceof BodyDeclaration) {
            Optional<Comment> comment = ((BodyDeclaration<?>) node).getComment();
            if (comment.isPresent()) return comment.get().getContent().trim().replaceFirst("^/+", "").trim();
        }
        return "";
    }

    public boolean deprecated(NodeWithAnnotations<?> node) {
        return annotations.find(node, "Deprecated").isPresent()
                || (node instanceof NodeWithJavadoc && ((NodeWithJavadoc<?>) node).getJavadoc().map(j -> j.toText().contains("@deprecated")).orElse(false));
    }
}
