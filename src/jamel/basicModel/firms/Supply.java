package jamel.basicModel.firms;

import jamel.basicModel.banks.Cheque;

/**
 * Represents a supply.
 */
public interface Supply {

	/**
	 * Returns the unit price of the goods.
	 * 
	 * @return the unit price of the goods.
	 */
	double getPrice();

	/**
	 * Returns the supplier.
	 * 
	 * @return the supplier.
	 */
	Supplier getSupplier();

	/**
	 * Returns the total value of the supply.
	 * 
	 * @return the total value of the supply.
	 */
	long getTotalValue();

	/**
	 * Returns <code>true</code> if this supply is empty (no goods to sell).
	 * 
	 * @return <code>true</code> if this supply is empty.
	 */
	boolean isEmpty();

	/**
	 * Purchases some goods.
	 * 
	 * @param volume
	 *            the volume to be purchased.
	 * 
	 * @param cheque
	 *            the payment cheque.
	 * 
	 * @return the purchased goods.
	 */
	Goods purchase(int volume, Cheque cheque);

	/**
	 * Returns the volume of this supply.
	 * 
	 * @return the volume of this supply.
	 */
	int getVolume();

}
