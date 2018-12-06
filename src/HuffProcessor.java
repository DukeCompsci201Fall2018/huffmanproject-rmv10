/**
 * Although this class has a history of several years, it is starting from a
 * blank-slate, new and clean implementation as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information and including
 * debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in  Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		while (true) {
			int val = in.readBits(BITS_PER_WORD);

			if (val == -1) {
				break;
			}

			out.writeBits(BITS_PER_WORD, val);
		}

		out.close();
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		// Check if the file starts with the Huffman Coding magic number
		int firstBits = in.readBits(BITS_PER_INT);
		if (firstBits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + firstBits);
		}

		// Read the tree header
		HuffNode root = readTreeHeader(in);

		// Process the bits and write them out
		decompressBits(in, out, root);

		out.close();
	}

	/**
	 * Reads the Huffman Tree header of a compressed file.
	 * 
	 * @param in file stream
	 * @return the root node of the tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		// Read a single bit
		int bit = in.readBits(1);

		if (bit == -1) {
			throw new HuffException("Bit is -1");
		}

		// If internal --> Recursively read left then right
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		} else {	// Leaf --> Read value
			// Read nine bits from input
			int value = in.readBits(BITS_PER_WORD);
			return new HuffNode(value, 0, null, null);
		}
	}

	/**
	 * Decompresses a Huffman-encoded bit stream and writes it out.
	 * 
	 * @param in compressed bit stream
	 * @param out target for uncompressed data
	 */
	private void decompressBits(BitInputStream in, BitOutputStream out, HuffNode root) {
		HuffNode current = root;

		while (true) {
			// Read next bit
			int bits = in.readBits(1);

			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) {
					current = current.myLeft;
				} else {
					current = current.myRight;
				}

				// If current is a leaf node...
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break;	// Stop processing
					} else {
						// Write bits for current value
						out.write(current.myValue);
						current = root;
					}
				}
			}
		}
	}
}
