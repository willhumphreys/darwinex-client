'use client'
import {useEffect, useState} from 'react';
import {Account, Query, Trade} from "@/app/interfaces";
import TradesTable from "@/app/tradesTable";
import QueryBuilder from "@/app/queryBuilder";
import {fetchTrades} from '@/app/fetchTrades';
import {fetchAccounts} from '@/app/fetchAccounts';

export default function FetchTradesClient() {


    const [accounts, setAccounts] = useState<Account[]>([]);
    const [trades, setTrades] = useState<Trade[]>([]);
    const [sortColumn, setSortColumn] = useState('id');  // Default sort column
    const [sortDirection, setSortDirection] = useState('ASC');  // Default sort direction


    const fetchAllAccounts = async () => {
        const fetchedAccounts = await fetchAccounts();
        if (fetchedAccounts !== null) {
            setAccounts(fetchedAccounts);
        }
    };

    const [query, setQuery] = useState<Query>({
        id: null, account: {id: null}, setup: {

            id: null,
            createdDateTime: '',
            symbol: '',
            rank: null,
            dayOfWeek: null,
            hourOfDay: null,
            stop: null,
            limit: null,
            tickOffset: null,
            tradeDuration: null,
            outOfTime: null
        },
        type: '',
        createdDateTime: '',
        placedDateTime: '',
        targetPlaceDateTime: '',
        placedPrice: '',
        filledDateTime: '',
        filledPrice: '',
        closedDateTime: '',
        closedPrice: '',
        closeType: '',
        message: ''
    });

    const updateTrades = async () => {
        const fetchedTrades = await fetchTrades(query, sortColumn, sortDirection);
        if (fetchedTrades !== null) {
            setTrades(fetchedTrades);
        }
    };

    useEffect(() => {
        fetchAllAccounts();
        updateTrades();
    }, [sortColumn, sortDirection, query]);

    const handleHeaderClick = (newSortColumn: string) => {
        if (newSortColumn === sortColumn) {
            setSortDirection(sortDirection === 'ASC' ? 'DESC' : 'ASC');
        } else {
            setSortColumn(newSortColumn);
            setSortDirection('ASC');
        }
        updateTrades();
    };


    return (<section className="bg-gray-100 py-10 min-h-screen">
        <div className="container mx-auto">
            <div className="w-full h-16 bg-gray-700 flex items-center pl-6 space-x-4">
                <select className="mt-1 p-2 border rounded-md"
                        onChange={(e) => {
                            const accountId = e.target.value;

                            setQuery({
                                ...query, account: {
                                    ...query.account, id: accountId === '' ? null : parseInt(accountId)
                                }

                            })
                        }}>
                    <option value="all">All</option>
                    {accounts.map((account, index) => (<option key={index} value={account.id}>
                        {account.name}
                    </option>))}

                </select>
            </div>

            <h1 className="text-3xl font-bold mb-4">Trades</h1>
            <div className="mb-6 flex flex-wrap items-center">
                <QueryBuilder query={query} setQuery={setQuery}/>
                <button onClick={updateTrades}
                        className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded">
                    Apply Filters
                </button>

            </div>

            <div>
                <TradesTable
                    trades={trades}
                    handleHeaderClick={handleHeaderClick}
                />
            </div>

        </div>
    </section>);
}