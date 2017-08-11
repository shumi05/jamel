package jamel.v170804.models.basicModel1.firms;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jamel.util.Agent;
import jamel.util.ArgChecks;
import jamel.util.JamelObject;
import jamel.util.NotUsedException;
import jamel.util.Parameters;
import jamel.util.Sector;
import jamel.v170804.data.AgentDataset;
import jamel.v170804.data.BasicAgentDataset;
import jamel.v170804.models.basicModel1.banks.Account;
import jamel.v170804.models.basicModel1.banks.Bank;
import jamel.v170804.models.basicModel1.banks.Cheque;
import jamel.v170804.models.basicModel1.households.Shareholder;
import jamel.v170804.models.basicModel1.households.Worker;

/**
 * A basic firm.
 */
public class BasicFirm extends JamelObject implements Agent, Firm, Employer, Supplier {

	/**
	 * The data keys.
	 */
	private static final BasicFirmKeys keys = new BasicFirmKeys();

	/**
	 * To test the validity of job contracts.
	 * 
	 * @return a predicate which returns <code>true</code> for not valid
	 *         contracts.
	 */
	final private static Predicate<LaborContract> isNotValid() {
		return contract -> !contract.isValid();
	}

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
		case "planProduction":
			action = (agent) -> {
				((BasicFirm) agent).planProduction();
			};
			break;
		case "production":
			action = (agent) -> {
				((BasicFirm) agent).production();
			};
			break;
		case "payWages":
			action = (agent) -> {
				((BasicFirm) agent).payWages();
			};
			break;
		case "payDividends":
			action = (agent) -> {
				((BasicFirm) agent).payDividends();
			};
			break;
		default:
			throw new IllegalArgumentException(phaseName);
		}

		return action;

	}

	/**
	 * The bank account of this firm.
	 */
	private final Account account;

	/**
	 * The dataset.
	 */
	final private AgentDataset dataset;

	/**
	 * The factory.
	 */
	final private Factory factory;

	/**
	 * The id of this firm.
	 */
	final private int id;

	/**
	 * The normal volume of inventories.
	 */
	final private double inventoriesNormalVolume;

	/**
	 * Maximal duration of a job contract.
	 */
	final private int jobContractMax;

	/**
	 * Minimal duration of a job contract.
	 */
	final private int jobContractMin;

	/**
	 * The job offer of this firm.
	 */
	private final BasicJobOffer jobOffer;

	/**
	 * The markup.
	 */
	private Double markup = null;

	/**
	 * The flexibility of the markup.
	 */
	final private double markupFlexibility;

	/**
	 * The owners of the firm.
	 */
	private List<Shareholder> owners = new LinkedList<>();

	/**
	 * The list of the labor contracts.
	 */
	private final LinkedList<BasicLaborContract> payroll = new LinkedList<>();

	/**
	 * The parent sector.
	 */
	final private Sector sector;

	/**
	 * The supply of this firm.
	 */
	private final BasicSupply supply;

	/**
	 * The wage. Exogenously fixed. TODO endogenize.
	 */
	private final long wage;

	/**
	 * Creates a new firm.
	 * 
	 * @param sector
	 *            the sector of this firm.
	 * @param id
	 *            the id of this firm.
	 */
	public BasicFirm(final Sector sector, final int id) {
		super(sector.getSimulation());
		this.sector = sector;

		final Parameters params = this.sector.getParameters();
		ArgChecks.nullNotPermitted(params, "params");

		this.id = id;
		this.dataset = new BasicAgentDataset(this, keys);

		this.markup = params.get("pricing").getDoubleAttribute("initialMarkup");
		this.markupFlexibility = params.get("pricing").getDoubleAttribute("markupFlexibility");
		this.jobContractMax = params.get("workforce").get("jobContracts").getIntAttribute("max");
		this.jobContractMin = params.get("workforce").get("jobContracts").getIntAttribute("min");
		this.wage = params.get("workforce").getIntAttribute("wage");

		final String bankSectorName = params.get("financing").getAttribute("bankSector");
		this.account = ((Bank) this.getSimulation().getSector(bankSectorName).select(1)[0]).openAccount(this);

		this.factory = new BasicFactory(params.get("production"), this);
		this.jobOffer = new BasicJobOffer(this);
		this.supply = new BasicSupply(this);

		this.inventoriesNormalVolume = this.factory.getCapacity() * this.factory.getProductivity()
				* params.get("inventories").getDoubleAttribute("normalVolumeRatio");

	}

	/**
	 * Initializes the owners of this firm.
	 */
	private void initOwners() {
		final Agent[] selection = this.getSimulation().getSector("Shareholders").select(10);
		for (int i = 0; i < selection.length; i++) {
			if (selection[i] != null) {
				this.owners.add((Shareholder) selection[i]);
			}
		}
	}

	/**
	 * The dividend payment phase.
	 */
	private void payDividends() {
		if (this.owners.isEmpty()) {
			throw new RuntimeException("No owners.");
		}

		final long cash = this.account.getAmount();
		final long assets = cash + this.factory.getValue();
		final long liabilities = this.account.getDebt();
		final long capital = assets - liabilities;
		final long capitalTarget = (long) (assets * 0.5);
		final long capitalExcess = Math.max(capital - capitalTarget, 0);
		if (capitalExcess > this.owners.size()) {
			final long newDividend = Math.min(this.account.getAmount(), capitalExcess) / this.owners.size();
			if (newDividend > 0) {
				for (final Shareholder shareholder : this.owners) {
					shareholder.acceptDividendCheque(this.account.issueCheque(shareholder, newDividend));
				}
			}
		}
	}

	/**
	 * The wage payment phase.
	 */
	private void payWages() {

		/*
		 * Première passe : on calcule le wagebill.
		 */

		long wages = 0;
		for (BasicLaborContract contract : this.payroll) {
			wages += contract.getWage();
		}

		/*
		 * Besoin d'un financement ?
		 */

		if (wages > this.account.getAmount()) {
			this.account.borrow(wages - this.account.getAmount(), 12, false);
		}

		/*
		 * Deuxième passe : on paie.
		 */

		for (BasicLaborContract contract : this.payroll) {
			contract.getWorker().acceptPayCheque(this.account.issueCheque(contract.getWorker(), contract.getWage()));
		}

	}

	/**
	 * Phase of production planing. Decides how much to produce.
	 */
	private void planProduction() {

		// On commence par faire le ménage dans la liste des contrats de
		// travail, en retirant les contrats échus.

		this.payroll.removeIf(isNotValid());

		// Déterminer le niveau souhaité de la production.
		final int workforceTarget = (int) (this.getRandom().nextFloat() * this.factory.getCapacity());
		// TODO: le caractère aléatoire de l'objectif de production est
		// évidemment
		// provisoire.

		// Licencier ou embaucher.
		if (this.payroll.size() > workforceTarget) {
			do {
				// Last in first out.
				this.payroll.removeLast().breach();
			} while (workforceTarget < this.payroll.size());
		} else if (this.payroll.size() < workforceTarget) {
			this.jobOffer.setWage(this.wage);
			this.jobOffer.setVacancies(workforceTarget - this.payroll.size());
		}
		this.dataset.put(keys.jobOffers, this.jobOffer.getVacancies());
	}

	/**
	 * The production phase.
	 */
	private void production() {
		this.factory.production(this.payroll);
		if (this.factory.getInventories().getVolume() > 0) {
			// Updates the price
			final double newPrice = this.markup * this.factory.getInventories().getValue()
					/ this.factory.getInventories().getVolume();
			final double oldPrice = this.supply.getPrice();
			if (Math.abs(newPrice / oldPrice - 1.) > 0.05) {
				this.supply.update(this.factory.getInventories().getVolume(), newPrice);
			} else {
				this.supply.update(this.factory.getInventories().getVolume(), oldPrice);
			}

			this.dataset.put(keys.supplyVolume, this.factory.getInventories().getVolume());
			this.dataset.put(keys.supplyValue, this.supply.getPrice() * this.factory.getInventories().getVolume());
			this.dataset.put(keys.supplyCost, this.factory.getInventories().getValue());
		}
	}

	/**
	 * Updates the markup.
	 */
	private void updateMarkup() {
		double delta = this.markupFlexibility * this.getRandom().nextDouble();
		if (this.factory.getInventories().getVolume() > this.inventoriesNormalVolume) {
			delta = -delta;
		}
		this.markup += delta;
		if (this.markup < 0.1) {
			this.markup = 0.1;
		}
		this.dataset.put(keys.deltaMarkup, delta);
	}

	/**
	 * Accepts the specified cheque.
	 * 
	 * @param cheque
	 *            a cheque to be deposited on the firm account.
	 */
	void accept(Cheque cheque) {
		this.account.deposit(cheque);
	}

	/**
	 * Returns the dataset of this firm.
	 * 
	 * @return the dataset of this firm.
	 */
	AgentDataset getDataset() {
		return this.dataset;
	}

	/**
	 * Returns a new job contract for the specified worker.
	 * 
	 * @param worker
	 *            the applicant.
	 * @return a new job contract.
	 */
	LaborContract getNewJobContract(Worker worker) {
		final int term = this.jobContractMin + this.getRandom().nextInt(this.jobContractMax);
		final BasicLaborContract contract = new BasicLaborContract(getSimulation(), this, worker,
				this.jobOffer.getWage(), term);
		this.payroll.add(contract);
		return contract;
	}

	/**
	 * Returns the specified volume of goods.
	 * 
	 * @param volume
	 *            the volume of goods to be returned.
	 * @return the specified volume of goods.
	 */
	Goods supply(long volume) {
		return this.factory.getInventories().take(volume);
	}

	/**
	 * Closes the firm at the end of the period.
	 */
	@Override
	public void close() {
		this.supply.updateData();
		this.dataset.put(keys.count, 1);
		this.dataset.put(keys.inventoriesVolume, this.factory.getInventories().getVolume());
		this.dataset.put(keys.inventoriesNormalVolume, this.inventoriesNormalVolume);
		this.dataset.put(keys.inventoriesValue, this.factory.getInventories().getValue());
		this.dataset.put(keys.money, this.account.getAmount());
		this.dataset.put(keys.assets, this.account.getAmount() + this.factory.getValue());
		this.dataset.put(keys.tangibleAssets, this.factory.getValue());
		this.dataset.put(keys.liabilities, this.account.getDebt());
		this.dataset.put(keys.markup, this.markup);
		this.dataset.close();
		this.supply.close();
		this.jobOffer.close();
		this.factory.close();
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
	public JobOffer getJobOffer() {
		final JobOffer result;
		if (this.jobOffer.isEmpty()) {
			result = null;
		} else {
			result = this.jobOffer;
		}
		return result;
	}

	@Override
	public String getName() {
		return "firm_" + this.id;
	}

	@Override
	public Sector getSector() {
		return this.sector;
	}

	@Override
	public Supply getSupply() {
		final Supply result;
		if (!supply.isEmpty()) {
			result = this.supply;
		} else {
			result = null;
		}
		return result;
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
	 * Opens the firm at the beginning of the period.
	 */
	@Override
	public void open() {
		if (this.owners.isEmpty()) {
			initOwners();
		}
		this.jobOffer.open();
		this.dataset.open();
		this.supply.open();
		this.factory.open();
		super.open();
		// this.
		this.updateMarkup();
	}

}
