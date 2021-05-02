from electroncash.address import Address
from Oregano.oregano.address import Address as ERGAddy
bch_address = input("Provide BCH address for conversion: ")
a = Address.from_string(bch_address)
e  = ERGAddy(a.hash160,0)
print(e.to_full_string(0))


