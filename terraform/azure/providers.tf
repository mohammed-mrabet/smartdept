terraform {
  required_version = ">= 1.6"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
  # État partagé (facultatif) : décommenter après avoir créé le compte de stockage
  # backend "azurerm" {
  #   resource_group_name  = "smartdept-tfstate"
  #   storage_account_name = "smartdepttfstate"
  #   container_name       = "tfstate"
  #   key                  = "smartdept.tfstate"
  # }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}
