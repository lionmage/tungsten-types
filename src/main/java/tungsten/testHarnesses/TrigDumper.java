package tungsten.testHarnesses;

import tungsten.types.functions.impl.Sin;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;

public class TrigDumper {
    private static final MathContext ctx = new MathContext(8);
    private static final Sin sin = new Sin(ctx);

    public static void main(String[] args) {
        RealType increment = new RealImpl(args[0], ctx);
        File f = new File(args[1]);
        System.out.println("Beginning. Attempting to open " + f.getPath() + " for writing.");
        if (f.exists()) {
            System.err.println(f.getPath() + " already exists, overwriting!");
        } else {
            try {
                boolean result = f.createNewFile();
                if (!result) System.err.println("File " + f.getPath() + " was not successfully created.");
                result &= f.setWritable(true);  // just to be sure
                if (result) System.out.println("Ready to write");
            } catch (IOException e) {
                System.err.println("File IO problems...");
                e.printStackTrace();
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
            writer.write("x,y");
            writer.newLine();
            RealType end = new RealImpl(BigDecimal.valueOf(3L), ctx);
            RealType val = new RealImpl(BigDecimal.ZERO, ctx);
            while (val.compareTo(end) <= 0) {
                writer.write(val + "," + sin.apply(val));
                writer.newLine();
                val = (RealType) val.add(increment);
            }
        } catch (IOException e) {
            System.err.println("Problem writing file " + f.getPath());
            e.printStackTrace();
        }
    }
}
