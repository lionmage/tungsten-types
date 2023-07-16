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

import tungsten.types.functions.curvefit.CurveFittingStrategy;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.Set;

/**
 * Annotation processor to ensure that {@code @StrategySupprts} annotations are
 * only applied to classes implementing {@link CurveFittingStrategy}. This
 * processor also checks the integrity of the annotation's parameters and will
 * raise errors or warnings as appropriate.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class StrategySupportsProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        final Messager messager = processingEnv.getMessager();
        for (TypeElement typeElement : annotations) {
            for (Element element : env.getElementsAnnotatedWith(typeElement)) {
                StrategySupports annotation = element.getAnnotation(StrategySupports.class);
                if (annotation != null) {
                    if (annotation.name() != null && annotation.name().length() < 2) {
                        messager.printMessage(Diagnostic.Kind.WARNING,
                                "The name specified is too short for meaningful filtering.", element);
                    }
                    if (annotation.type() == null) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "A CurveType must be specified for this strategy to be discoverable.", element);
                    }
                }
                TypeMirror typeMirror = element.asType();

                if (!isSubclassOfCurveFittingStrategy(typeMirror)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "Any type annotated with @StrategySupports must implement the CurveFittingStrategy interface.", element);
                }
            }
        }
        return false;
    }

    private static final String CFS = CurveFittingStrategy.class.getName();

    private boolean isSubclassOfCurveFittingStrategy(TypeMirror t) {
        if (processingEnv.getTypeUtils().directSupertypes(t).stream().map(TypeMirror::toString).anyMatch(CFS::equals)) return true;
        return processingEnv.getTypeUtils().directSupertypes(t).stream().anyMatch(this::isSubclassOfCurveFittingStrategy);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(StrategySupports.class.getName());
    }
}
