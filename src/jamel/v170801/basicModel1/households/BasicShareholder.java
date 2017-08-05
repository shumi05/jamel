package jamel.v170801.basicModel1.households;

import java.util.Arrays;
import java.util.function.Consumer;

import jamel.util.Agent;
import jamel.util.JamelObject;
import jamel.util.NotUsedException;
import jamel.util.Parameters;
import jamel.util.Sector;
import jamel.v170801.basicModel1.banks.Account;
import jamel.v170801.basicModel1.banks.Amount;
import jamel.v170801.basicModel1.banks.Bank;
import jamel.v170801.basicModel1.banks.Cheque;
import jamel.v170801.basicModel1.firms.Goods;
import jamel.v170801.basicModel1.firms.Supplier;
import jamel.v170801.basicModel1.firms.Supply;
import jamel.v170801.data.AgentDataset;

/**
 * Represents a shareholder.
 */
public class BasicShareholder extends JamelObject implements Agent, Shareholder {

	/**
	 * Returns the specified action.
	 * 
	 * @param phaseName
	 *            the name of the action.
	 * @return the specified action.
	 */
	static public Consumer<? super Agent> getAction(final String phaseName) {

		final Consumer<? super Agent> action;

		switch (phaseName) {
		case "consumption":
			action = (agent) -> {
				((BasicShareholder) agent).consumption();
			};
			break;
		default:
			throw new IllegalArgumentException(phaseName);
		}

		return action;

	}

	/**
	 * The bank account of this shareholder.
	 */
	private final Account account;

	/**
	 * The dataset.
	 */
	final private AgentDataset dataset;

	/**
	 * The amount of the dividends received for this period.
	 */
	private final Amount dividends = new Amount();

	/**
	 * The id of this agent.
	 */
	final private int id;

	/**
	 * The saving propensity.
	 */
	private final double param_savingPropensity;

	/**
	 * The number of suppliers to be selected in the consumption phase.
	 */
	private final int param_supplySearch;

	/**
	 * The parent sector.
	 */
	final private Sector sector;

	/**
	 * The sector of the suppliers.
	 */
	private final Sector supplierSector;

	/**
	 * Creates a new shareholder.
	 * 
	 * @param sector
	 *            the sector of this shareholder.
	 * @param id
	 *            the id of this shareholder.
	 */
	public BasicShareholder(final Sector sector, final int id) {
		super(sector.getSimulation());
		this.sector = sector;
		this.id = id;

		final Parameters params = this.sector.getParameters();
		if (params == null) {
			throw new RuntimeException("Parameters are null.");
		}

		final String bankSectorName = params.getAttribute("bankSector");
		this.account = ((Bank) this.getSimulation().getSector(bankSectorName).select(1)[0]).openAccount(this);

		final Parameters goodMarketParams = params.get("goodMarket");
		this.supplierSector = this.getSimulation().getSector(goodMarketParams.getAttribute("suppliers"));
		this.param_savingPropensity = goodMarketParams.getDoubleAttribute("savingPropensity");
		this.param_supplySearch = goodMarketParams.getIntAttribute("search");

		this.dataset = new AgentDataset(this);
	}

	/**
	 * The consumption phase.
	 */
	private void consumption() {
		long consumptionVolume = 0;
		long consumptionValue = 0;
		int supplierSample = 0;
		int suppliers = 0;
		long budget = this.account.getAmount();
		this.dataset.put("consumptionBudget", budget);
		if (budget > 0) {
			final Agent[] selection = this.supplierSector.select(param_supplySearch);
			Arrays.sort(selection, Households.supplierComparator);
			supplierSample = selection.length;

			for (Agent agent : selection) {
				if (agent == null || ((Supplier) agent).getSupply() == null || ((Supplier) agent).getSupply().isEmpty()
						|| ((Supplier) agent).getSupply().getPrice() > budget) {
					break;
				}
				
				
				final Supply supply = ((Supplier) agent).getSupply();
				final long spending;
				final long consumVol;
				if (supply.getTotalValue() <= budget) {
					consumVol = supply.getVolume();
					spending = (long) (consumVol * supply.getPrice());
					if (spending != supply.getTotalValue()) {
						throw new RuntimeException("Inconsistency.");
					}
				} else {
					consumVol = (int) (budget / supply.getPrice());
					spending = (long) (consumVol * supply.getPrice());
				}
								
				final Goods goods = supply.purchase(consumVol,
						this.account.issueCheque(supply.getSupplier(), spending));
				if (goods.getVolume() != consumVol) {
					throw new RuntimeException("Bad volume");
				}
				budget -= spending;
				consumptionValue += spending;
				consumptionVolume += goods.getVolume();
				goods.consume();
				suppliers++;
				
			}

		}
				
		this.dataset.put("consumptionVolume", consumptionVolume);
		this.dataset.put("consumptionValue", consumptionValue);
		this.dataset.put("suppliers", suppliers);
		this.dataset.put("supplierSample", supplierSample);
		// TODO updater les chiffres de l'épargne.
	}

	@Override
	public void acceptDividendCheque(Cheque cheque) {
		this.account.deposit(cheque);
		// TODO comptabiliser ce dépôt pour calculer le revenu de l'agent.
		// a des fins statistiques mais aussi comportementales.
	}

	/**
	 * Closes the shareholder at the end of the period.
	 */
	@Override
	public void close() {
		this.dataset.put("count", 1);
		this.dataset.put("money", this.account.getAmount());
		this.dataset.close();
		super.close();
	}

	@Override
	public Long getAssetTotalValue() {
		throw new NotUsedException();
	}

	@Override
	public int getBorrowerStatus() {
		throw new NotUsedException();
	}

	@Override
	public Double getData(String dataKey, int period) {
		return this.dataset.getData(dataKey, period);
	}

	@Override
	public String getName() {
		return "shareholder_" + this.id;
	}

	@Override
	public Sector getSector() {
		return this.sector;
	}

	@Override
	public void goBankrupt() {
		throw new NotUsedException();
	}

	@Override
	public boolean isBankrupted() {
		throw new NotUsedException();
	}

	@Override
	public boolean isSolvent() {
		throw new NotUsedException();
	}

	/**
	 * Opens the shareholder at the beginning of the period.
	 */
	@Override
	public void open() {
		this.dividends.cancel();
		this.dataset.open();
		super.open();
	}

}