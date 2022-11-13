package tungsten.types.annotations;

import tungsten.types.numerics.ComplexType;

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
 * Annotation processor to ensure that {@code @Polar} annotations are
 * only applied to classes implementing {@link ComplexType}.
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 */
public class PolarProcessor extends AbstractProcessor {
    public PolarProcessor() { super(); }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        final Messager messager = processingEnv.getMessager();
        for (TypeElement typeElement : annotations) {
            for (Element element : env.getElementsAnnotatedWith(typeElement)) {
                // if Polar ever gets any values, they can be extracted and validated here
//                Polar annotation = element.getAnnotation(Polar.class);
                TypeMirror typeMirror = element.asType();

                if (!isSubclassOfComplex(typeMirror)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "Any type annotated with @Polar must implement the ComplexType interface.", element);
                }
            }
        }
        return false;
    }

    private static final String CPLX = ComplexType.class.getName();

    private boolean isSubclassOfComplex(TypeMirror t) {
        if (processingEnv.getTypeUtils().directSupertypes(t).stream().map(TypeMirror::toString).anyMatch(CPLX::equals)) return true;
        return processingEnv.getTypeUtils().directSupertypes(t).stream().anyMatch(this::isSubclassOfComplex);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Polar.class.getName());
    }
}
