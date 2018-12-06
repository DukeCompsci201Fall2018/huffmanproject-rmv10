import java.util.*;

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
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();

		writeCompressedBits(codings, in, out);

		out.close();
	}

	/**
	 * Determines the frequency of every 8-bit chunk in a file.
	 * 
	 * @param in file stream
	 * @return array of frequencies for each character
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] result = new int[ALPH_SIZE + 1];	// 257

		for (int i = 0; i < result.length; i++) {
			result[i] = 0;
		}

		// Stop when we reach -1
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			
			if (bits == -1) {
				break;
			}

			result[bits]++;
		}

		// Indicate that there is one occurrence of PSEUDO_EOF
		result[PSEUDO_EOF] = 1;

		return result;
	}

	/**
	 * Creates a Huffman Tree from frequency counts of bytes.
	 * 
	 * @param counts frequency of each byte in the file
	 * @return the root node of the new tree
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		// Set up the priority queue
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {	// Skip chars that don't appear
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}

		// Add nodes to tree in order of weight
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();

			HuffNode t = new HuffNode(
				0, 	// Value
				left.myWeight + right.myWeight, 	// Weight
				left, 	// Subtrees
				right
			);

			pq.add(t);
		}

		HuffNode root = pq.remove();

		return root;
	}

	/**
	 * Creates codings for each byte using a Huffman tree.
	 * 
	 * @param root root of the Huffman tree to use
	 * @return Huffman encoding for every byte
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];

		// *Spongebob meme*
		// ~~ rEcUrSiOn iS uSeFuL ~~
		codingsHelper(root, "", encodings);

		return encodings;
	}

	/**
	 * Recursive helper method for `makeCodingsFromTree()`.
	 * 
	 * @param root root of the tree
	 * @param path current path to leaf
	 * @param encodings array of encodings for every byte
	 */
	private void codingsHelper(HuffNode root, String path, String[] encodings) {
		// If root is leaf, add encoding value for the leaf's value
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		} else {
			// Explore left path if possible
			if (root.myLeft instanceof HuffNode) {
				codingsHelper(root.myLeft, path + "0", encodings);
			}

			// Explore right path if possible
			if (root.myRight instanceof HuffNode) {
				codingsHelper(root.myRight, path + "1", encodings);
			}
		}
	}

	/**
	 * Write a Huffman tree as a file header.
	 * 
	 * @param root root node of the Huffman tree
	 * @param out output file stream
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		// If leaf node --> Write single bit 1, then value of node
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);	// Leaf node
			out.writeBits(BITS_PER_WORD + 1, root.myValue);	// 9 bits
		} else {	// Internal --> write single bit 0, then recurse
			out.writeBits(1, 0);	// Internal node

			// Write left subtree, if possible
			if (root.myLeft instanceof HuffNode) {
				writeHeader(root.myLeft, out);
			}

			// Write right subtree, if possible
			if (root.myRight instanceof HuffNode) {
				writeHeader(root.myRight, out);
			}
		}
	}

	/**
	 * Write the compressed bits to an output file.
	 * 
	 * @param codings Huffman codings for each byte
	 * @param in input (uncompressed) file stream
	 * @param out target (compressed) file stream
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);	// 8
			
			// if file is done, write out an encoded EOF
			if (bits == -1) {
				String coded = codings[PSEUDO_EOF];
				out.writeBits(coded.length(), Integer.parseInt(coded, 2));

				break;
			}

			// Encode the bits
			String coded = codings[bits];

			// Convert the encoded value to a bit string
			int codedBits = Integer.parseInt(coded, 2);	// base 2

			// Write the encoded value as a bit string
			out.writeBits(coded.length(), codedBits);
		}
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

		System.out.println("Done reading tree header.");

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
			int value = in.readBits(BITS_PER_WORD + 1);	// 9
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
			System.out.println("Reading next bit...");

			// Read next bit
			int bits = in.readBits(1);

			System.out.println("Bit is " + bits);

			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) {
					System.out.println("Takling left path");
					current = current.myLeft;
				} else {
					System.out.println("Taking right path");
					current = current.myRight;
				}

				// If current is a leaf node...
				if (current.myLeft == null && current.myRight == null) {
					System.out.println("Is a leaf");
					if (current.myValue == PSEUDO_EOF) {
						break;	// Stop processing
					} else {
						// Write bits for current value
						System.out.println("Writing: " + current.myValue);
						out.writeBits(BITS_PER_WORD, current.myValue);	// 8 bits
						current = root;
					}
				}
			}
		}
	}
}
