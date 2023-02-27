/*
 * The MIT License
 *
 * Copyright © 2019 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This little gem is cribbed liberally from
 * <a href="http://chriswu.me/blog/a-lru-cache-in-10-lines-of-java/">this entry
 * on Chris Wu's blog</a>.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int cacheSize;

    public LRUCache(int cacheSize) {
        super(16, 0.75f, true);  // this superclass constructor specifies access order instead of insertion order
        this.cacheSize = cacheSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() >= cacheSize;
    }
    
    public int getCacheSize() { return cacheSize; }
}
