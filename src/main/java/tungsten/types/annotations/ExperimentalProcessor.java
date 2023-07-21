package tungsten.types.annotations;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.*;

public class ExperimentalProcessor extends AbstractProcessor implements TaskListener {
    private Trees trees;
    private final Set<Name> funcNames = new HashSet<>();

    public ExperimentalProcessor() { super(); }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
        // TODO determine whether it's appropriate to use setTaskListener() here instead
        JavacTask.instance(processingEnv).addTaskListener(this);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Experimental.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_11;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        final Messager messager = processingEnv.getMessager();
        for (TypeElement typeElement : annotations) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(typeElement)) {
                // if Experimental ever gets any values, they can be extracted and validated here
//                Experimental annotation = element.getAnnotation(Experimental.class);
//                TypeMirror typeMirror = element.asType();
                for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                    TypeElement annotationElement = (TypeElement) mirror.getAnnotationType().asElement();
                    if (annotationElement.getQualifiedName().contentEquals(Experimental.class.getName())) {
                        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> kv :
                                mirror.getElementValues().entrySet()) {
                            ExecutableElement member = kv.getKey();
                            if (member.getKind() != ElementKind.METHOD && member.getKind() != ElementKind.CLASS) {
                                messager.printMessage(Diagnostic.Kind.ERROR,
                                        "@Experimental annotation must apply to a class or method.",
                                        element);
                            }
                            if (member.getKind() == ElementKind.METHOD) {
                                funcNames.add(member.getSimpleName());
                                messager.printMessage(Diagnostic.Kind.NOTE,
                                        "Method " + member.getSimpleName() +
                                        " is marked as experimental, and its signature may " +
                                        "change in the future, or the method may be removed entirely.", element);
                                // flag any private experimental APIs as suspicious
                                if (member.getModifiers().contains(Modifier.PRIVATE)) {
                                    messager.printMessage(Diagnostic.Kind.WARNING,
                                            "Method " + member.getSimpleName() +
                                            " is marked private. Making an experimental API private " +
                                            "does not make much sense. Consider removing it.", element);
                                }
                            }
                        }
                        // we found what we were looking for
                        break;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void finished(TaskEvent event) {
        if (event.getKind() == TaskEvent.Kind.ANALYZE) {
            event.getCompilationUnit().accept(new TreeScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree methodInv, Void v) {
                    ExpressionTree methodSelect = methodInv.getMethodSelect();
                    switch (methodSelect.getKind()) {
                        case IDENTIFIER:
                            IdentifierTree identifier = (IdentifierTree) methodSelect;
                            // an extra check to ensure we're only printing warnings for experimental methods
                            if (funcNames.contains(identifier.getName())) {
                                trees.printMessage(Diagnostic.Kind.WARNING,
                                        "Call to an experimental API: " + identifier.getName() +
                                                " [API is subject to change or removal]",
                                        methodSelect,
                                        event.getCompilationUnit());
                            }
                            break;
                        case COMPILATION_UNIT:
                            CompilationUnitTree compilationUnit = (CompilationUnitTree) methodSelect;
                            TreePath path = new TreePath(compilationUnit);
                            Element method = trees.getElement(path);
                            Experimental experimental = method.getAnnotation(Experimental.class);
                            if (experimental != null) {
                                trees.printMessage(Diagnostic.Kind.WARNING,
                                        "Call to an experimental API: " + method.getSimpleName() +
                                        " [API is subject to change or removal]",
                                        compilationUnit, event.getCompilationUnit());
                            }
                            break;
                        default:
                            break;
                    }
                    return super.visitMethodInvocation(methodInv, v);
                }
            }, null);
        }
    }
}
