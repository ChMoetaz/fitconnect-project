import os
import sys

from azure.identity import DefaultAzureCredential
from azure.mgmt.compute import ComputeManagementClient
from azure.mgmt.network import NetworkManagementClient
from azure.mgmt.resource import ResourceManagementClient


def env(name, default=None, required=False):
    val = os.environ.get(name, default)
    if required and not val:
        sys.exit(f"missing required env var: {name}")
    return val


SUBSCRIPTION_ID = env("AZURE_SUBSCRIPTION_ID", required=True)
LOCATION = env("AZURE_LOCATION", "westeurope")
RESOURCE_GROUP = env("AZURE_RESOURCE_GROUP", "fitconnect-rg")

VNET_NAME = env("AZURE_VNET_NAME", "fitconnect-vnet")
SUBNET_NAME = env("AZURE_SUBNET_NAME", "fitconnect-subnet")
PUBLIC_IP_NAME = env("AZURE_PUBLIC_IP_NAME", "fitconnect-pip")
NSG_NAME = env("AZURE_NSG_NAME", "fitconnect-nsg")
NIC_NAME = env("AZURE_NIC_NAME", "fitconnect-nic")
VM_NAME = env("AZURE_VM_NAME", "fitconnect-vm")

VM_SIZE = env("AZURE_VM_SIZE", "Standard_B2s")
ADMIN_USERNAME = env("AZURE_ADMIN_USERNAME", "moetaz")
SSH_PUBLIC_KEY_PATH = env("AZURE_SSH_PUBLIC_KEY_PATH", os.path.expanduser("~/.ssh/id_rsa.pub"))

SECURITY_RULES = [
    ("SSH", 300, "22"),
    ("HTTP", 310, "80"),
    ("HTTPS", 320, "443"),
]


def read_ssh_public_key():
    if not os.path.isfile(SSH_PUBLIC_KEY_PATH):
        sys.exit(f"ssh public key not found: {SSH_PUBLIC_KEY_PATH}")
    with open(SSH_PUBLIC_KEY_PATH, encoding="utf-8") as fh:
        return fh.read().strip()


def main():
    credential = DefaultAzureCredential()
    resource_client = ResourceManagementClient(credential, SUBSCRIPTION_ID)
    network_client = NetworkManagementClient(credential, SUBSCRIPTION_ID)
    compute_client = ComputeManagementClient(credential, SUBSCRIPTION_ID)

    ssh_key = read_ssh_public_key()

    print(f"resource group {RESOURCE_GROUP} ({LOCATION})")
    resource_client.resource_groups.create_or_update(
        RESOURCE_GROUP, {"location": LOCATION}
    )

    print(f"vnet {VNET_NAME}")
    network_client.virtual_networks.begin_create_or_update(
        RESOURCE_GROUP, VNET_NAME,
        {"location": LOCATION, "address_space": {"address_prefixes": ["10.0.0.0/16"]}},
    ).result()

    print(f"subnet {SUBNET_NAME}")
    subnet = network_client.subnets.begin_create_or_update(
        RESOURCE_GROUP, VNET_NAME, SUBNET_NAME, {"address_prefix": "10.0.0.0/24"}
    ).result()

    print(f"public ip {PUBLIC_IP_NAME}")
    public_ip = network_client.public_ip_addresses.begin_create_or_update(
        RESOURCE_GROUP, PUBLIC_IP_NAME,
        {
            "location": LOCATION,
            "sku": {"name": "Standard"},
            "public_ip_allocation_method": "Static",
        },
    ).result()

    print(f"nsg {NSG_NAME}")
    nsg = network_client.network_security_groups.begin_create_or_update(
        RESOURCE_GROUP, NSG_NAME,
        {
            "location": LOCATION,
            "security_rules": [
                {
                    "name": f"Allow-{name}",
                    "priority": priority,
                    "direction": "Inbound",
                    "access": "Allow",
                    "protocol": "Tcp",
                    "source_port_range": "*",
                    "destination_port_range": port,
                    "source_address_prefix": "*",
                    "destination_address_prefix": "*",
                }
                for name, priority, port in SECURITY_RULES
            ],
        },
    ).result()

    print(f"nic {NIC_NAME}")
    nic = network_client.network_interfaces.begin_create_or_update(
        RESOURCE_GROUP, NIC_NAME,
        {
            "location": LOCATION,
            "ip_configurations": [
                {
                    "name": "ipconfig1",
                    "subnet": {"id": subnet.id},
                    "public_ip_address": {"id": public_ip.id},
                }
            ],
            "network_security_group": {"id": nsg.id},
        },
    ).result()

    print(f"vm {VM_NAME}")
    compute_client.virtual_machines.begin_create_or_update(
        RESOURCE_GROUP, VM_NAME,
        {
            "location": LOCATION,
            "hardware_profile": {"vm_size": VM_SIZE},
            "storage_profile": {
                "image_reference": {
                    "publisher": "Canonical",
                    "offer": "0001-com-ubuntu-server-jammy",
                    "sku": "22_04-lts-gen2",
                    "version": "latest",
                },
                "os_disk": {
                    "create_option": "FromImage",
                    "managed_disk": {"storage_account_type": "Standard_LRS"},
                },
            },
            "os_profile": {
                "computer_name": VM_NAME,
                "admin_username": ADMIN_USERNAME,
                "linux_configuration": {
                    "disable_password_authentication": True,
                    "ssh": {
                        "public_keys": [
                            {
                                "path": f"/home/{ADMIN_USERNAME}/.ssh/authorized_keys",
                                "key_data": ssh_key,
                            }
                        ]
                    },
                },
            },
            "network_profile": {"network_interfaces": [{"id": nic.id}]},
        },
    ).result()

    ip = network_client.public_ip_addresses.get(RESOURCE_GROUP, PUBLIC_IP_NAME)
    print(f"done. public ip: {ip.ip_address}")


if __name__ == "__main__":
    main()
