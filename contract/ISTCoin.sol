pragma solidity ^0.8.0;

contract ISTCoin {
    string public constant name = "IST Coin";
    string public constant symbol = "IST";
    uint8 public constant decimals = 2;
    uint256 public constant totalSupply = 100_000_000;
    bool private initialized;

    mapping(address => uint256) private balances;
    mapping(address => mapping(address => uint256)) private allowances;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    function init(address owner) public {
        require(owner != address(0), "zero address");
        require(!initialized, "already initialized");
        initialized = true;
        balances[owner] = totalSupply;
        emit Transfer(address(0), owner, totalSupply);
    }

    function balanceOf(address account) public view returns (uint256) {
        return balances[account];
    }

    function allowance(address owner, address spender) public view returns (uint256) {
        return allowances[owner][spender];
    }

    function transfer(address to, uint256 amount) public returns (bool) {
        require(to != address(0), "zero address");
        require(balances[msg.sender] >= amount, "insufficient balance");
        balances[msg.sender] -= amount;
        balances[to] += amount;
        emit Transfer(msg.sender, to, amount);
        return true;
    }

    function approve(address spender, uint256 amount) public returns (bool) {
        require(spender != address(0), "zero address");
        uint256 current = allowances[msg.sender][spender];
        require(current == 0 || amount == 0, "must reset allowance to zero first");
        allowances[msg.sender][spender] = amount;
        emit Approval(msg.sender, spender, amount);
        return true;
    }

    function increaseAllowance(address spender, uint256 added) public returns (bool) {
        require(spender != address(0), "zero address");
        allowances[msg.sender][spender] += added;
        emit Approval(msg.sender, spender, allowances[msg.sender][spender]);
        return true;
    }

    function decreaseAllowance(address spender, uint256 subtracted) public returns (bool) {
        require(spender != address(0), "zero address");
        uint256 current = allowances[msg.sender][spender];
        require(current >= subtracted, "decrease below zero");
        allowances[msg.sender][spender] = current - subtracted;
        emit Approval(msg.sender, spender, allowances[msg.sender][spender]);
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) public returns (bool) {
        require(to != address(0), "zero address");
        require(balances[from] >= amount, "insufficient balance");
        uint256 allowed = allowances[from][msg.sender];
        require(allowed >= amount, "insufficient allowance");

        allowances[from][msg.sender] = allowed - amount;
        balances[from] -= amount;
        balances[to] += amount;

        emit Approval(from, msg.sender, allowances[from][msg.sender]);
        emit Transfer(from, to, amount);
        return true;
    }
}
